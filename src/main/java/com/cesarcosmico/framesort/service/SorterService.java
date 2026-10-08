package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.config.SorterSettings;
import com.cesarcosmico.framesort.item.Keys;
import com.cesarcosmico.framesort.model.BlockKey;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/** Sorters are spread over the ticks so they never all act at once. */
public final class SorterService {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    // Shulker boxes inside bundles inside shulker boxes…: deep enough for any real setup, and bounded.
    private static final int MAX_DEPTH = 8;
    // A sorter frame whose dispenser sits in an unloaded chunk looks again this often.
    private static final long RETRY_TICKS = 100;

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
    private final Map<BlockKey, Sorter> sorters = new HashMap<>();
    // Frames with a retry pending; every load, reload or frame change would otherwise start another retry chain.
    private final Set<UUID> waiting = new HashSet<>();
    private long tick;
    private int nextPhase;

    public SorterService(Plugin plugin, Supplier<FrameSortSettings> settings, DeliveryService delivery) {
        this.plugin = plugin;
        this.settings = settings;
        this.delivery = delivery;
    }

    public void consider(ItemFrame frame) {
        boolean holdsActivator = holdsActivator(frame);
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
            return;
        }
        Block block = FrameGeometry.attachedBlock(frame);
        BlockKey key = FrameGeometry.key(block);
        Sorter existing = sorters.get(key);
        if (holdsActivator && block.getType() == Material.DISPENSER) {
            if (existing == null) {
                SorterSettings sorter = settings.get().sorter();
                sorters.put(key, new Sorter(frame, block, tick + 1 + (nextPhase++ % sorter.tickRate())));
                applyLook(frame, block, true);
            }
        } else if (existing != null && existing.frame.equals(frame)) {
            sorters.remove(key);
            applyLook(frame, block, false);
        }
    }

    /** Re-checks a frame on the next tick: when an item-frame event fires, the frame still holds the old item. */
    public void considerLater(ItemFrame frame) {
        frame.getScheduler().run(plugin, task -> consider(frame), null);
    }

    /** The frame left the world because its chunk unloaded; the sorter comes back with the chunk. */
    public void forget(ItemFrame frame) {
        sorters.values().removeIf(sorter -> sorter.frame.equals(frame));
    }

    public void broken(ItemFrame frame) {
        Block block = FrameGeometry.attachedBlock(frame);
        Sorter sorter = sorters.get(FrameGeometry.key(block));
        if (sorter != null && sorter.frame.equals(frame)) {
            sorters.remove(FrameGeometry.key(block));
            applyLook(frame, block, false);
        }
    }

    public boolean isSorter(Block block) {
        return sorters.containsKey(FrameGeometry.key(block));
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

    public ItemStack createActivator(int amount) {
        return settings.get().sorter().activator().create(Keys.ACTIVATOR, "sorter", amount);
    }

    public void tick() {
        tick++;
        SorterSettings sorter = settings.get().sorter();
        for (Map.Entry<BlockKey, Sorter> entry : List.copyOf(sorters.entrySet())) {
            Sorter current = entry.getValue();
            if (current.due > tick) {
                continue;
            }
            if (!current.frame.isValid()) {
                sorters.remove(entry.getKey());
                continue;
            }
            if (!FrameGeometry.attachedLoaded(current.frame)) {
                current.due = tick + sorter.tickRate();
                continue;
            }
            if (!makesSorter(current.frame, current.block)) {
                sorters.remove(entry.getKey());
                applyLook(current.frame, current.block, false);
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

    private boolean holdsActivator(ItemFrame frame) {
        SorterSettings sorter = settings.get().sorter();
        return frame.isValid() && sorter.frameTypes().contains(frame.getType()) && sorter.isActivator(frame.getItem());
    }

    private boolean makesSorter(ItemFrame frame, Block block) {
        return holdsActivator(frame) && block.getType() == Material.DISPENSER;
    }

    private void dispense(Sorter sorter) {
        if (!(sorter.block.getState(false) instanceof Dispenser dispenser)) {
            return;
        }
        Slot slot = pick(dispenser.getInventory());
        if (slot == null) {
            return;
        }
        Location location = sorter.block.getLocation().toCenterLocation();
        if (delivery.deliver(sorter.block, new SlotSource(slot, location)) && settings.get().sorter().showActivity()) {
            sorter.frame.setRotation(sorter.frame.getRotation().rotateClockwise());
        }
    }

    private void applyLook(ItemFrame frame, Block block, boolean active) {
        SorterSettings sorter = settings.get().sorter();
        if (sorter.hideFrame() && frame.isValid()) {
            frame.setVisible(!active);
        }
        // A fresh snapshot, written back at once: it cannot carry stale items.
        if (block.getState() instanceof Dispenser dispenser) {
            Component name = active && sorter.customName() != null ? MINI_MESSAGE.deserialize(sorter.customName()) : null;
            if (!Objects.equals(dispenser.customName(), name)) {
                dispenser.customName(name);
                dispenser.update(true, false);
            }
        }
    }

    private static @Nullable Slot pick(Inventory inventory) {
        List<Integer> filled = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.isEmpty()) {
                filled.add(i);
            }
        }
        if (filled.isEmpty()) {
            return null;
        }
        Slot slot = new InventorySlot(inventory, filled.get(ThreadLocalRandom.current().nextInt(filled.size())));
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            Slot inner = innerSlot(slot);
            if (inner == null) {
                break;
            }
            slot = inner;
        }
        return slot;
    }

    private static @Nullable Slot innerSlot(Slot parent) {
        ItemStack holder = parent.get();
        ItemContainerContents container = holder.getData(DataComponentTypes.CONTAINER);
        if (container != null) {
            Integer index = randomFilled(container.contents());
            return index == null ? null : new ContainerSlot(parent, index);
        }
        BundleContents bundle = holder.getData(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null) {
            Integer index = randomFilled(bundle.contents());
            return index == null ? null : new BundleSlot(parent, index);
        }
        return null;
    }

    private static @Nullable Integer randomFilled(List<ItemStack> items) {
        List<Integer> filled = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isEmpty()) {
                filled.add(i);
            }
        }
        Collections.shuffle(filled, ThreadLocalRandom.current());
        return filled.isEmpty() ? null : filled.getFirst();
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

    private record SlotSource(Slot slot, Location location) implements DeliveryService.Source {
        @Override
        public ItemStack stack() {
            return slot.get();
        }

        @Override
        public void commit(int remaining) {
            slot.set(remaining <= 0 ? ItemStack.empty() : slot.get().asQuantity(remaining));
        }

        @Override
        public void moveTo(Location destination) {
            ItemStack rest = slot.get().clone();
            destination.getWorld().dropItem(destination, rest, item -> item.setVelocity(new Vector()));
            slot.set(ItemStack.empty());
        }

        @Override
        public void destroy() {
            slot.set(ItemStack.empty());
        }
    }
}
