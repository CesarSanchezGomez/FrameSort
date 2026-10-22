package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.config.SorterSettings;
import com.cesarcosmico.framesort.item.FrameCodec;
import com.cesarcosmico.framesort.item.ItemOverlay;
import com.cesarcosmico.framesort.item.ItemTemplate;
import com.cesarcosmico.framesort.item.Keys;
import com.cesarcosmico.framesort.item.MarkerCodec;
import com.cesarcosmico.framesort.model.BlockKey;
import com.cesarcosmico.framesort.model.ItemReference;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Sorters are spread over the ticks so they never all act at once. */
public final class SorterService {

    /** What checking a frame did to its dispenser. */
    public enum Change { CREATED, REMOVED, UNCHANGED }

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    // Shulker boxes inside bundles inside shulker boxes…: deep enough for any real setup, and bounded.
    private static final int MAX_DEPTH = 8;
    // A sorter frame whose dispenser sits in an unloaded chunk looks again this often.
    private static final long RETRY_TICKS = 100;
    // A turn sends a stack's worth however the items are packed, so bundles of single items don't crawl; the same
    // bound caps the tries when items have nowhere to go, so one that doesn't fit never stalls the rest.
    private static final int PER_TURN = 64;
    // Turned by up to 5°, a copy this much bigger still covers the real block: it needs cos 5° + sin 5° ≈ 1.083.
    private static final float WOBBLE_SCALE = 1.1f;
    // The angles the copy turns to, one step each: a short shake that ends where it started.
    private static final List<Float> WOBBLE_DEGREES = List.of(5f, -5f, 0f);
    private static final int WOBBLE_STEP_TICKS = 2;
    private static final List<BlockFace> NEIGHBOURS = List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH,
            BlockFace.WEST, BlockFace.UP, BlockFace.DOWN);

    private static final class Sorter {
        private final ItemFrame frame;
        private final Block block;
        private long due;

        private Sorter(ItemFrame frame, Block block, long due) {
            this.frame = frame;
            this.block = block;
            this.due = due;
        }
    }

    private final Plugin plugin;
    private final Supplier<FrameSortSettings> settings;
    private final DeliveryService delivery;
    private final CustomItemResolver customItems;
    private final Map<BlockKey, Sorter> sorters = new HashMap<>();
    // Frames with a retry pending; every load, reload or frame change would otherwise start another retry chain.
    private final Set<UUID> waiting = new HashSet<>();
    // Kept apart from the sorters, so a copy still goes away when its sorter is broken while it wobbles.
    private final Map<BlockKey, BlockDisplay> wobbling = new HashMap<>();
    private long tick;
    private int nextPhase;

    public SorterService(Plugin plugin, Supplier<FrameSortSettings> settings, DeliveryService delivery,
                         CustomItemResolver customItems) {
        this.plugin = plugin;
        this.settings = settings;
        this.delivery = delivery;
        this.customItems = customItems;
    }

    public Change consider(ItemFrame frame) {
        boolean holdsActivator = settings.get().sorter().isActivatorFrame(frame, frame.getItem(), customItems::id);
        if (!FrameGeometry.attachedLoaded(frame)) {
            // Only frames that could make a sorter wait for the neighbouring chunk; the rest are never sorters.
            if (holdsActivator && waiting.add(frame.getUniqueId())) {
                Runnable done = () -> waiting.remove(frame.getUniqueId());
                ScheduledTask retry = frame.getScheduler().runDelayed(plugin, task -> {
                    done.run();
                    consider(frame);
                }, done, RETRY_TICKS);
                if (retry == null) {
                    done.run();
                }
            }
            return Change.UNCHANGED;
        }
        Block block = FrameGeometry.attachedBlock(frame);
        BlockKey key = FrameGeometry.key(block);
        Sorter existing = sorters.get(key);
        if (holdsActivator && block.getType() == Material.DISPENSER) {
            if (existing == null) {
                SorterSettings sorter = settings.get().sorter();
                sorters.put(key, new Sorter(frame, block, tick + 1 + (nextPhase++ % sorter.tickRate())));
            }
            // On every check, not only the first: a reload's look settings then reach the sorters that exist.
            if (existing == null || existing.frame.equals(frame)) {
                applyLook(frame, true);
            }
            return existing == null ? Change.CREATED : Change.UNCHANGED;
        }
        if (existing != null && existing.frame.equals(frame)) {
            sorters.remove(key);
            applyLook(frame, false);
            return Change.REMOVED;
        }
        return Change.UNCHANGED;
    }

    /**
     * Re-checks a frame on the next tick: when an item-frame event fires, the frame still holds the old item. A
     * sorter belongs to whoever put the activator in, not to whoever hung the frame; {@code placer} is {@code null}
     * when the change was not putting an item in, such as rotating the frame of a sorter not yet created.
     */
    public void considerLater(ItemFrame frame, @Nullable UUID placer, Consumer<Change> then) {
        frame.getScheduler().run(plugin, task -> {
            Change change = consider(frame);
            if (change == Change.CREATED && placer != null) {
                FrameCodec.setOwner(frame, placer);
            }
            then.accept(change);
        }, null);
    }

    /** The owner of the sorter on {@code block}: the owner of its frame, or {@code null} when nobody owns it. */
    public @Nullable UUID ownerOf(Block block) {
        Sorter sorter = sorters.get(FrameGeometry.key(block));
        return sorter == null ? null : FrameCodec.owner(sorter.frame);
    }

    /** The frame left the world because its chunk unloaded; the sorter comes back with the chunk. */
    public void forget(ItemFrame frame) {
        sorters.values().removeIf(sorter -> sorter.frame.equals(frame));
    }

    /** @return whether the frame made a sorter */
    public boolean broken(ItemFrame frame) {
        Sorter sorter = sorterOf(frame);
        if (sorter == null) {
            return false;
        }
        sorters.remove(FrameGeometry.key(sorter.block));
        applyLook(frame, false);
        return true;
    }

    /** The dispenser {@code frame} makes a sorter, or {@code null} when it makes none. */
    public @Nullable Block blockOf(ItemFrame frame) {
        Sorter sorter = sorterOf(frame);
        return sorter == null ? null : sorter.block;
    }

    private @Nullable Sorter sorterOf(ItemFrame frame) {
        Sorter sorter = sorters.get(FrameGeometry.key(FrameGeometry.attachedBlock(frame)));
        return sorter != null && sorter.frame.equals(frame) ? sorter : null;
    }

    /**
     * Shows every loaded sorter's frame again, so nothing stays hidden while FrameSort is off, and removes the copies
     * that are still wobbling.
     */
    public void stopAll() {
        sorters.values().forEach(sorter -> applyLook(sorter.frame, false));
        // Removing one retires its scheduler, whose callback takes it out of the map.
        List.copyOf(wobbling.values()).forEach(Entity::remove);
        wobbling.clear();
    }

    public boolean isSorter(Block block) {
        return sorters.containsKey(FrameGeometry.key(block));
    }

    /** The title a sorter's inventory shows instead of the dispenser's own name, or {@code null} to keep that name. */
    public @Nullable Component title(Block block) {
        String title = settings.get().sorter().customName();
        return title != null && isSorter(block) ? MINI_MESSAGE.deserialize(title) : null;
    }

    public void wake(Block block) {
        Sorter sorter = sorters.get(FrameGeometry.key(block));
        if (sorter != null) {
            sorter.due = Math.min(sorter.due, tick + 1);
        }
    }

    public List<Block> blocks() {
        return sorters.values().stream().map(sorter -> sorter.block).toList();
    }

    /** {@code null} when CraftEngine can't build the activator now: an ID it doesn't know, or a /ce reload. */
    public @Nullable ItemStack createActivator(int amount) {
        SorterSettings sorter = settings.get().sorter();
        ItemStack item = switch (sorter.activator()) {
            case ItemReference.Vanilla(Material material) ->
                    new ItemTemplate(material, sorter.activatorComponents(), sorter.activatorLook()).create(amount);
            case ItemReference.Custom custom -> {
                ItemStack built = customItems.item(custom, amount);
                yield built == null ? null : ItemOverlay.apply(built, sorter.activatorLook());
            }
        };
        return item == null ? null : MarkerCodec.mark(item, Keys.ACTIVATOR, "sorter");
    }

    public void tick() {
        tick++;
        SorterSettings sorter = settings.get().sorter();
        for (Map.Entry<BlockKey, Sorter> entry : List.copyOf(sorters.entrySet())) {
            Sorter current = entry.getValue();
            if (current.due > tick) {
                continue;
            }
            if (!FrameGeometry.attachedLoaded(current.frame)) {
                current.due = tick + sorter.tickRate();
                continue;
            }
            if (!makesSorter(current.frame, current.frame.getItem())) {
                sorters.remove(entry.getKey());
                applyLook(current.frame, false);
                continue;
            }
            if (sorter.disableWhenPowered() && current.block.isBlockPowered()) {
                current.due = tick + 1;
                continue;
            }
            current.due = tick + sorter.tickRate();
            dispense(current);
        }
    }

    /** Whether {@code item} in {@code frame} makes a sorter of the block the frame hangs on. */
    public boolean makesSorter(ItemFrame frame, ItemStack item) {
        return FrameGeometry.attachedBlock(frame).getType() == Material.DISPENSER
                && settings.get().sorter().isActivatorFrame(frame, item, customItems::id);
    }

    private void dispense(Sorter sorter) {
        UUID owner = FrameCodec.owner(sorter.frame);
        int sent = 0;
        // What found nowhere to go this turn is not tried again.
        List<ItemStack> missed = new ArrayList<>();
        for (int tries = 0; tries < PER_TURN && sent < PER_TURN; tries++) {
            // Other plugins' handlers run during a delivery and may break the dispenser.
            if (!(sorter.block.getState(false) instanceof Dispenser dispenser)) {
                return;
            }
            Slot slot = pick(dispenser.getInventory(), missed);
            if (slot == null) {
                break;
            }
            ItemStack tried = slot.get();
            int kept = Math.max(0, tried.getAmount() - (PER_TURN - sent));
            int moved = delivery.deliver(sorter.block, owner, new SlotSource(slot, kept));
            if (moved == 0) {
                missed.add(tried);
            }
            sent += moved;
        }
        if (sent == 0 || !(sorter.block.getState(false) instanceof Dispenser dispenser)) {
            return;
        }
        PersistentDataContainer data = dispenser.getPersistentDataContainer();
        long total = data.getOrDefault(Keys.SORTED, PersistentDataType.LONG, 0L) + sent;
        data.set(Keys.SORTED, PersistentDataType.LONG, total);
        if (settings.get().sorter().showActivity()) {
            sorter.frame.setRotation(sorter.frame.getRotation().rotateClockwise());
            wobble(sorter.block);
        }
    }

    /** A slightly bigger copy of the dispenser shakes for a moment over it, then goes. */
    private void wobble(Block block) {
        BlockKey key = FrameGeometry.key(block);
        if (wobbling.containsKey(key)) {
            return;
        }
        BlockDisplay copy = block.getWorld().spawn(block.getLocation(), BlockDisplay.class, display -> {
            display.setPersistent(false);
            display.setBlock(block.getBlockData());
            display.setBrightness(brightness(block));
            display.setTransformationMatrix(wobbleMatrix(0));
        });
        // Another plugin may cancel the spawn.
        if (!copy.isValid()) {
            return;
        }
        wobbling.put(key, copy);
        wobbleStep(key, copy, 0);
    }

    private void wobbleStep(BlockKey key, BlockDisplay copy, int step) {
        copy.getScheduler().runDelayed(plugin, task -> {
            if (step == WOBBLE_DEGREES.size()) {
                wobbling.remove(key, copy);
                copy.remove();
                return;
            }
            copy.setInterpolationDelay(0);
            copy.setInterpolationDuration(WOBBLE_STEP_TICKS);
            copy.setTransformationMatrix(wobbleMatrix(WOBBLE_DEGREES.get(step)));
            wobbleStep(key, copy, step + 1);
        }, () -> wobbling.remove(key, copy), WOBBLE_STEP_TICKS);
    }

    // Around the block's centre: the model of a block display spans 0 to 1 from the entity's position.
    private static Matrix4f wobbleMatrix(float degrees) {
        return new Matrix4f()
                .translate(0.5f, 0.5f, 0.5f)
                .rotateY((float) Math.toRadians(degrees))
                .scale(WOBBLE_SCALE)
                .translate(-0.5f, -0.5f, -0.5f);
    }

    // Inside the opaque dispenser the copy would get light 0 and show black, so it takes its brightest neighbour's.
    // A neighbour in an unloaded chunk is skipped: reading its light would load that chunk.
    private static Display.Brightness brightness(Block block) {
        int blockLight = 0;
        int skyLight = 0;
        for (BlockFace face : NEIGHBOURS) {
            Block next = block.getRelative(face);
            if (next.getWorld().isChunkLoaded(next.getX() >> 4, next.getZ() >> 4)) {
                blockLight = Math.max(blockLight, next.getLightFromBlocks());
                skyLight = Math.max(skyLight, next.getLightFromSky());
            }
        }
        return new Display.Brightness(blockLight, skyLight);
    }

    /** How many items this sorter's dispenser has sent; the count stays with the block. */
    public long sorted(Block block) {
        return block.getState(false) instanceof Dispenser dispenser
                ? dispenser.getPersistentDataContainer().getOrDefault(Keys.SORTED, PersistentDataType.LONG, 0L)
                : 0;
    }

    private void applyLook(ItemFrame frame, boolean active) {
        frame.setVisible(!active || !settings.get().sorter().hideFrame());
    }

    /** A sorter opens a shulker box or bundle with contents first; it goes whole once nothing inside can go. */
    public static boolean opens(ItemStack item) {
        return !TargetResolver.contents(item).isEmpty();
    }

    private static @Nullable Slot pick(Inventory inventory, List<ItemStack> missed) {
        List<ItemStack> contents = Arrays.stream(inventory.getContents())
                .map(item -> item == null ? ItemStack.empty() : item).toList();
        for (int index : shuffledFilled(contents)) {
            Slot found = pick(new InventorySlot(inventory, index), contents.get(index), missed, 0);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Something in {@code slot} that has not missed this turn: a stack inside it first, or else the slot itself, so a
     * shulker box or bundle goes whole once nothing inside it can go. {@code item} is what the slot holds, read once.
     */
    private static @Nullable Slot pick(Slot slot, ItemStack item, List<ItemStack> missed, int depth) {
        if (missed.stream().anyMatch(item::isSimilar)) {
            return null;
        }
        if (depth < MAX_DEPTH) {
            ItemContainerContents container = item.getData(DataComponentTypes.CONTAINER);
            BundleContents bundle = item.getData(DataComponentTypes.BUNDLE_CONTENTS);
            List<ItemStack> inside = container != null ? container.contents()
                    : bundle != null ? bundle.contents() : List.of();
            for (int index : shuffledFilled(inside)) {
                Slot child = container != null ? new ContainerSlot(slot, index) : new BundleSlot(slot, index);
                Slot found = pick(child, inside.get(index), missed, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return slot;
    }

    private static List<Integer> shuffledFilled(List<ItemStack> items) {
        List<Integer> filled = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isEmpty()) {
                filled.add(i);
            }
        }
        Collections.shuffle(filled, ThreadLocalRandom.current());
        return filled;
    }

    private sealed interface Slot permits InventorySlot, ContainerSlot, BundleSlot {
        ItemStack get();

        void set(ItemStack stack);
    }

    private record InventorySlot(Inventory inventory, int index) implements Slot {
        @Override
        public ItemStack get() {
            ItemStack item = inventory.getItem(index);
            return item == null ? ItemStack.empty() : item;
        }

        @Override
        public void set(ItemStack stack) {
            inventory.setItem(index, stack.isEmpty() ? null : stack);
        }
    }

    private record ContainerSlot(Slot parent, int index) implements Slot {
        @Override
        public ItemStack get() {
            ItemContainerContents contents = parent.get().getData(DataComponentTypes.CONTAINER);
            return contents == null || index >= contents.contents().size()
                    ? ItemStack.empty() : contents.contents().get(index);
        }

        @Override
        public void set(ItemStack stack) {
            ItemStack holder = parent.get();
            ItemContainerContents contents = holder.getData(DataComponentTypes.CONTAINER);
            if (contents == null || index >= contents.contents().size()) {
                return;
            }
            List<ItemStack> items = new ArrayList<>(contents.contents());
            items.set(index, stack);
            holder.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(items));
            parent.set(holder);
        }
    }

    private record BundleSlot(Slot parent, int index) implements Slot {
        @Override
        public ItemStack get() {
            BundleContents contents = parent.get().getData(DataComponentTypes.BUNDLE_CONTENTS);
            return contents == null || index >= contents.contents().size()
                    ? ItemStack.empty() : contents.contents().get(index);
        }

        @Override
        public void set(ItemStack stack) {
            ItemStack holder = parent.get();
            BundleContents contents = holder.getData(DataComponentTypes.BUNDLE_CONTENTS);
            if (contents == null || index >= contents.contents().size()) {
                return;
            }
            List<ItemStack> items = new ArrayList<>(contents.contents());
            if (stack.isEmpty()) {
                items.remove(index);
            } else {
                items.set(index, stack);
            }
            holder.setData(DataComponentTypes.BUNDLE_CONTENTS, BundleContents.bundleContents(items));
            parent.set(holder);
        }
    }

    /** Offers all of the slot but {@code kept} items, which stay in it whatever happens: a turn never goes past 64. */
    private record SlotSource(Slot slot, int kept) implements DeliveryService.Source {
        @Override
        public ItemStack stack() {
            ItemStack item = slot.get();
            return item.asQuantity(item.getAmount() - kept);
        }

        @Override
        public void commit(int remaining) {
            leave(kept + remaining);
        }

        @Override
        public void moveTo(Location destination) {
            destination.getWorld().dropItem(destination, stack(), item -> item.setVelocity(new Vector()));
            leave(kept);
        }

        @Override
        public void destroy() {
            leave(kept);
        }

        private void leave(int amount) {
            slot.set(amount <= 0 ? ItemStack.empty() : slot.get().asQuantity(amount));
        }
    }
}
