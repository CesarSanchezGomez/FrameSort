package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.api.PadPullEvent;
import com.cesarcosmico.framesort.config.PadSettings;
import com.cesarcosmico.framesort.config.PadType;
import com.cesarcosmico.framesort.item.DropCodec;
import com.cesarcosmico.framesort.item.ItemOverlay;
import com.cesarcosmico.framesort.item.ItemTemplate;
import com.cesarcosmico.framesort.item.Keys;
import com.cesarcosmico.framesort.item.MarkerCodec;
import com.cesarcosmico.framesort.item.PadCodec;
import com.cesarcosmico.framesort.model.BlockKey;
import com.cesarcosmico.framesort.model.ChunkKey;
import com.cesarcosmico.framesort.model.ItemReference;
import com.cesarcosmico.framesort.model.PadEntry;
import com.cesarcosmico.framesort.model.PadMode;
import com.cesarcosmico.framesort.model.Pull;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/** Distances are in blocks. */
public final class PadService {

    private static final String CREATE_PERMISSION = "framesort.pad.create";

    public sealed interface Placement {
        record Created(PadType type) implements Placement {
        }

        record NotAllowed() implements Placement {
        }

        record NotAPad() implements Placement {
        }
    }

    /** Whether a pad's special block may be placed where a player is placing it. */
    public sealed interface Placing {
        record Fine() implements Placing {
        }

        /** It wouldn't complete a pad of {@code type}, whose base is called {@code base}. */
        record NeedsBase(PadType type, Component base) implements Placing {
        }

        /** CraftEngine is reloading, so its blocks can't be told apart yet. */
        record NotReady() implements Placing {
        }
    }

    /** What a break of a block may do, decided before it happens. */
    public sealed interface Breaking {
        /** {@code replacesDrops}: once it happens, the pad's special item drops instead of the block. */
        record Allowed(boolean replacesDrops) implements Breaking {
        }

        /** CraftEngine can't build the special item now, so the pad stays rather than lose it. */
        record Kept() implements Breaking {
        }
    }

    // A flight's rays start just off the block an item lies on and end just over the surface it lands on, so they don't
    // graze either.
    private static final double RAY_LIFT = 0.05;
    private static final int FIRST_MAIN_SLOT = 9;
    private static final int LAST_MAIN_SLOT = 35;

    private final Plugin plugin;
    private final Supplier<PadSettings> settings;
    private final DeliveryService delivery;
    private final CustomItemResolver customItems;
    private final Map<ChunkKey, Map<BlockKey, PadEntry>> pads = new HashMap<>();
    // Built before the break so a /ce reload before it happens can't lose the item; every break attempt replaces it.
    private final Map<BlockKey, ItemStack> handBack = new HashMap<>();
    private long tick;

    public PadService(Plugin plugin, Supplier<PadSettings> settings, DeliveryService delivery,
                      CustomItemResolver customItems) {
        this.plugin = plugin;
        this.settings = settings;
        this.delivery = delivery;
        this.customItems = customItems;
    }

    public void load(Chunk chunk) {
        Map<BlockKey, PadEntry> stored = PadCodec.read(chunk);
        if (!stored.isEmpty()) {
            pads.put(FrameGeometry.key(chunk), new LinkedHashMap<>(stored));
        }
    }

    public void unload(Chunk chunk) {
        pads.remove(FrameGeometry.key(chunk));
    }

    /** Registers a pad when {@code block}, just placed by {@code player} from {@code hand}, completes one. */
    public Placement registerPlaced(Player player, Block block, ItemStack hand) {
        PadSettings current = settings.get();
        String marker = MarkerCodec.read(hand, Keys.PAD_ITEM);
        PadBlock top = read(block);
        PadBlock base = read(block.getRelative(BlockFace.DOWN));
        boolean blocked = false;
        for (PadType type : current.types().values()) {
            if (!matches(top, base, type)) {
                continue;
            }
            boolean allowed = switch (current.creation()) {
                case ANYONE -> true;
                case PERMISSION -> player.hasPermission(CREATE_PERMISSION);
                case ITEM -> type.id().equals(marker);
            };
            if (allowed) {
                register(block, type, player.getUniqueId());
                return new Placement.Created(type);
            }
            blocked = true;
        }
        // In item mode a plain block is just a block: building with it is not an error worth a message.
        boolean silent = current.creation() == PadMode.ITEM && marker == null;
        return blocked && !silent ? new Placement.NotAllowed() : new Placement.NotAPad();
    }

    /**
     * In item mode, a special block that wouldn't complete its own pad where {@code block} was just placed from
     * {@code hand} (it would be lost as a plain block).
     */
    public Placing placing(Block block, ItemStack hand) {
        PadSettings current = settings.get();
        String marker = MarkerCodec.read(hand, Keys.PAD_ITEM);
        PadType type = marker != null && current.creation() == PadMode.ITEM ? current.type(marker) : null;
        if (type == null) {
            return new Placing.Fine();
        }
        PadBlock top = read(block);
        PadBlock base = read(block.getRelative(BlockFace.DOWN));
        boolean waitsForCraftEngine = top.unbound() && type.top() instanceof ItemReference.Custom
                || base.unbound() && type.base() instanceof ItemReference.Custom;
        if (waitsForCraftEngine) {
            return new Placing.NotReady();
        }
        return matches(top, base, type) ? new Placing.Fine() : new Placing.NeedsBase(type, name(type.base()));
    }

    private Component name(ItemReference block) {
        return switch (block) {
            case ItemReference.Vanilla vanilla -> Component.translatable(vanilla.material());
            case ItemReference.Custom custom -> {
                Component name = customItems.blockName(custom);
                yield name != null ? name : Component.text(custom.reference());
            }
        };
    }

    /**
     * Before other plugins decide whether {@code block} breaks; {@code drops} is whether the break drops items, as it
     * does outside creative mode.
     */
    public Breaking breaking(Block block, boolean drops) {
        BlockKey key = FrameGeometry.key(block);
        handBack.remove(key);
        PadType type = registered(block);
        boolean givesItemBack = drops && settings.get().creation() == PadMode.ITEM;
        if (type == null || !givesItemBack) {
            return new Breaking.Allowed(false);
        }
        ItemStack item = createItem(type, 1);
        if (item == null) {
            return new Breaking.Kept();
        }
        handBack.put(key, item);
        return new Breaking.Allowed(true);
    }

    /**
     * {@code block} broke: drops the special item built for it, if its drops are still off ({@code dropsRemoved}), and
     * forgets its pad. Returns whether it was one.
     */
    public boolean removeBrokenPad(Block block, boolean dropsRemoved) {
        ItemStack item = handBack.remove(FrameGeometry.key(block));
        // A break cancelled and let through again by other plugins never passed breaking(): an old item isn't its own.
        if (item != null && dropsRemoved) {
            block.getWorld().dropItemNaturally(block.getLocation().toCenterLocation(), item);
        }
        if (registered(block) == null) {
            return false;
        }
        unregister(block);
        return true;
    }

    public boolean isPadTop(Block block) {
        return registered(block) != null;
    }

    /** The pad whose top block this is, if it is registered and its column is still complete. */
    public @Nullable PadType padAt(Block top) {
        PadType type = stored(top);
        return type != null && matches(read(top), read(top.getRelative(BlockFace.DOWN)), type) ? type : null;
    }

    public @Nullable UUID ownerOf(Block top) {
        PadEntry entry = entry(top);
        return entry == null ? null : entry.owner();
    }

    public record Pad(Block top, PadType type, @Nullable UUID owner) {
    }

    /** The registered pads whose range reaches {@code center}, in its world. */
    public List<Pad> reaching(Block center) {
        BlockKey here = FrameGeometry.key(center);
        List<Pad> found = new ArrayList<>();
        for (Map<BlockKey, PadEntry> inChunk : pads.values()) {
            for (Map.Entry<BlockKey, PadEntry> pad : inChunk.entrySet()) {
                BlockKey key = pad.getKey();
                PadType type = settings.get().type(pad.getValue().type());
                if (type != null && key.distanceSquared(here) <= (long) type.range() * type.range()) {
                    found.add(new Pad(center.getWorld().getBlockAt(key.x(), key.y(), key.z()), type,
                            pad.getValue().owner()));
                }
            }
        }
        return List.copyOf(found);
    }

    /** {@code null} when CraftEngine can't build the item of the pad's top block now. */
    public @Nullable ItemStack createItem(PadType type, int amount) {
        ItemStack item = switch (type.top()) {
            case ItemReference.Vanilla(Material material) ->
                    new ItemTemplate(material, Map.of(), type.look()).create(amount);
            case ItemReference.Custom custom -> {
                ItemStack built = customItems.blockItem(custom, amount);
                yield built == null ? null : ItemOverlay.applyReplacingLore(built, type.look());
            }
        };
        return item == null ? null : MarkerCodec.mark(item, Keys.PAD_ITEM, type.id());
    }

    public void tick() {
        tick++;
        int interval = settings.get().sweepInterval();
        int phase = (int) (tick % interval);
        List<Map.Entry<BlockKey, PadEntry>> due = new ArrayList<>();
        for (Map<BlockKey, PadEntry> inChunk : pads.values()) {
            for (Map.Entry<BlockKey, PadEntry> pad : inChunk.entrySet()) {
                if (Math.floorMod(pad.getKey().hashCode(), interval) == phase) {
                    due.add(Map.entry(pad.getKey(), pad.getValue()));
                }
            }
        }
        for (Map.Entry<BlockKey, PadEntry> pad : due) {
            BlockKey key = pad.getKey();
            World world = plugin.getServer().getWorld(key.world());
            if (world != null) {
                sweep(world.getBlockAt(key.x(), key.y(), key.z()), pad.getValue());
            }
        }
    }

    private void sweep(Block top, PadEntry pad) {
        PadType type = settings.get().type(pad.type());
        // A type taken out of pads.yml, or mistyped, keeps its pads: they work again once it is back.
        if (type == null) {
            return;
        }
        PadBlock seen = read(top);
        if (!isTop(seen, type)) {
            unregister(top);
            return;
        }
        if (!matches(seen, read(top.getRelative(BlockFace.DOWN)), type)) {
            return;
        }
        Location above = top.getLocation().add(0.5, 1.5, 0.5);
        for (Item item : top.getWorld().getNearbyEntitiesByType(Item.class, above, 0.5, 0.5, 0.5)) {
            if (item.isValid()) {
                delivery.deliver(top, pad.owner(), type.range(), new EntitySource(item));
            }
        }
        if (type.pullRadius() > 0) {
            pull(top, type.pullRadius());
        }
        if (type.takesFromInventory()) {
            takeInventories(top, pad, type, above);
        }
    }

    private void takeInventories(Block top, PadEntry pad, PadType type, Location above) {
        for (Player player : top.getWorld().getNearbyEntitiesByType(Player.class, above, 0.5, 0.5, 0.5)) {
            if (!isCrouchingOnTop(player, top)) {
                continue;
            }
            PlayerInventory inventory = player.getInventory();
            List<Integer> filled = new ArrayList<>();
            for (int index = FIRST_MAIN_SLOT; index <= LAST_MAIN_SLOT; index++) {
                if (inventory.getItem(index) != null) {
                    filled.add(index);
                }
            }
            Collections.shuffle(filled, ThreadLocalRandom.current());
            for (int index : filled) {
                // Other plugins' handlers run during a delivery and may kill the player or make them leave.
                if (!player.isValid() || delivery.deliver(top, pad.owner(), type.range(),
                        new SlotSource(new Slot.InventorySlot(inventory, index), 0)) > 0) {
                    break;
                }
            }
        }
    }

    private static boolean isCrouchingOnTop(Player player, Block top) {
        return player.isSneaking() && player.getGameMode() != GameMode.SPECTATOR
                && Players.location(player).getBlock().equals(top.getRelative(BlockFace.UP));
    }

    private void pull(Block top, int radius) {
        if (!hasRoomToLand(top)) {
            return;
        }
        Block above = top.getRelative(BlockFace.UP);
        Location surface = top.getLocation().add(0.5, 0, 0.5);
        surface.setY(above.isPassable() ? above.getY() : above.getBoundingBox().getMaxY());
        // The pad's top slows a lying item even under a carpet or slab.
        double grip = Pull.grip(top.getType().getSlipperiness());
        for (Item item : top.getWorld().getNearbyEntitiesByType(Item.class, surface, radius, radius, radius)) {
            if (!isPullable(item, surface, radius)) {
                continue;
            }
            Location from = item.getLocation();
            if (Pull.needsHop(surface.getX() - from.getX(), surface.getZ() - from.getZ())) {
                hop(item, top, surface, grip);
            } else {
                throwOnto(item, top, surface, grip);
            }
        }
    }

    private static boolean hasRoomToLand(Block top) {
        Block above = top.getRelative(BlockFace.UP);
        return (above.isPassable() || above.getBoundingBox().getMaxY() < above.getY() + 1)
                && top.getRelative(BlockFace.UP, 2).isPassable();
    }

    private boolean isPullable(Item item, Location surface, int radius) {
        return isLooseOnGround(item) && liesInReach(item.getLocation(), surface, radius);
    }

    // Handlers of an earlier PadPullEvent may remove items.
    private static boolean isLooseOnGround(Item item) {
        return item.isValid() && item.isOnGround() && !thrownThisSweep(item) && !DropCodec.isMarked(item);
    }

    private boolean liesInReach(Location from, Location surface, int radius) {
        return from.distanceSquared(surface) <= (double) radius * radius && !inPadColumn(from, surface)
                && !isPadTop(from.getBlock().getRelative(BlockFace.DOWN));
    }

    // Another pad threw it, and it still lies on the ground until its own tick.
    private static boolean thrownThisSweep(Item item) {
        return item.getVelocity().getY() > 0;
    }

    private static boolean inPadColumn(Location at, Location surface) {
        return Math.abs(at.getX() - surface.getX()) < 0.5 && Math.abs(at.getZ() - surface.getZ()) < 0.5;
    }

    private static void throwOnto(Item item, Block top, Location surface, double grip) {
        Location from = item.getLocation();
        Pull.Arc arc = Pull.toPad(surface.getX() - from.getX(), surface.getY() - from.getY(),
                surface.getZ() - from.getZ(), grip);
        if (arc == null) {
            return;
        }
        Location previous = from.clone().add(0, RAY_LIFT, 0);
        List<Pull.Offset> path = arc.path();
        for (int index = 0; index < path.size(); index++) {
            Pull.Offset point = path.get(index);
            Location next = from.clone().add(point.x(), point.y() + (index == path.size() - 1 ? RAY_LIFT : 0),
                    point.z());
            if (blocked(previous, next)) {
                return;
            }
            previous = next;
        }
        if (new PadPullEvent(item, top).callEvent()) {
            item.setVelocity(vector(arc.velocity()));
        }
    }

    private void hop(Item item, Block top, Location surface, double grip) {
        Location from = item.getLocation();
        Pull.Hop hop = Pull.hop(surface.getX() - from.getX(), surface.getY() - from.getY(),
                surface.getZ() - from.getZ());
        // The drift down onto the pad runs above the line from the top of the hop to where it lands; the ray to the
        // centre, a little past that, only adds the air over the pad, which the checks above already keep free.
        Location peak = from.clone().add(0, hop.height(), 0);
        if (blocked(from.clone().add(0, RAY_LIFT, 0), peak) || blocked(peak, surface.clone().add(0, RAY_LIFT, 0))
                || !new PadPullEvent(item, top).callEvent()) {
            return;
        }
        item.setVelocity(vector(hop.velocity()));
        // Harmless if FrameSort is gone by then, so the task isn't kept.
        item.getScheduler().runDelayed(plugin, task -> {
            Location at = item.getLocation();
            // Water, a piston or another plugin may have moved it since.
            Pull.Velocity drift = item.isOnGround() ? null : Pull.drift(surface.getX() - at.getX(),
                    surface.getY() - at.getY(), surface.getZ() - at.getZ(), item.getVelocity().getY(), grip);
            if (drift != null) {
                item.setVelocity(vector(drift));
            }
        }, null, hop.ticks());
    }

    private static boolean blocked(Location from, Location to) {
        Vector line = to.toVector().subtract(from.toVector());
        // The corner where a throw comes over the pad's edge repeats the point before it on a tick that neither rises
        // nor falls, and a ray needs a direction.
        if (line.lengthSquared() == 0) {
            return false;
        }
        return from.getWorld().rayTraceBlocks(from, line.clone().normalize(), line.length(), FluidCollisionMode.NEVER,
                true) != null;
    }

    private static Vector vector(Pull.Velocity velocity) {
        return new Vector(velocity.x(), velocity.y(), velocity.z());
    }

    private static boolean matches(PadBlock top, PadBlock base, PadType type) {
        return top.is(type.top()) && base.is(type.base());
    }

    private static boolean isTop(PadBlock block, PadType type) {
        return block.is(type.top()) || type.top() instanceof ItemReference.Custom && block.unbound();
    }

    private PadBlock read(Block block) {
        String id = customItems.blockId(block);
        return new PadBlock(block.getType(), id, id == null && customItems.isCustomBlock(block));
    }

    private @Nullable PadType registered(Block block) {
        PadType type = stored(block);
        return type != null && isTop(read(block), type) ? type : null;
    }

    private @Nullable PadType stored(Block block) {
        PadEntry entry = entry(block);
        return entry == null ? null : settings.get().type(entry.type());
    }

    private @Nullable PadEntry entry(Block block) {
        Map<BlockKey, PadEntry> inChunk = pads.get(FrameGeometry.key(block.getChunk()));
        return inChunk == null ? null : inChunk.get(FrameGeometry.key(block));
    }

    private void register(Block block, PadType type, UUID owner) {
        Map<BlockKey, PadEntry> inChunk = pads.computeIfAbsent(FrameGeometry.key(block.getChunk()),
                key -> new LinkedHashMap<>());
        inChunk.put(FrameGeometry.key(block), new PadEntry(type.id(), owner));
        PadCodec.write(block.getChunk(), inChunk);
    }

    private void unregister(Block block) {
        ChunkKey chunk = FrameGeometry.key(block.getChunk());
        Map<BlockKey, PadEntry> inChunk = pads.get(chunk);
        if (inChunk != null && inChunk.remove(FrameGeometry.key(block)) != null) {
            PadCodec.write(block.getChunk(), inChunk);
            if (inChunk.isEmpty()) {
                pads.remove(chunk);
            }
        }
    }

    private record PadBlock(Material type, @Nullable String customId, boolean unbound) {
        boolean is(ItemReference reference) {
            return !unbound && reference.matches(type, customId);
        }
    }

    private record EntitySource(Item item) implements DeliveryService.Source {
        @Override
        public ItemStack stack() {
            return item.getItemStack();
        }

        @Override
        public void commit(int remaining) {
            if (remaining <= 0) {
                item.remove();
            } else {
                item.setItemStack(item.getItemStack().asQuantity(remaining));
            }
        }

        @Override
        public @Nullable Item moveTo(Location destination) {
            if (!item.teleport(destination)) {
                return null;
            }
            item.setVelocity(new Vector());
            return item;
        }

        @Override
        public void destroy() {
            item.remove();
        }
    }
}
