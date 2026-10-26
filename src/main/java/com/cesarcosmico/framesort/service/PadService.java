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

/** Pads live in their chunk's data, so only loaded pads are swept; sweeping also catches items that arrive late. */
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

    public sealed interface Breaking {
        /** The pad is gone; {@code item} is the special item to drop instead of the block, if any. */
        record Removed(@Nullable ItemStack item) implements Breaking {
        }

        /** The pad stays, so the special item it would give back is never lost. */
        record Kept() implements Breaking {
        }

        record NotAPad() implements Breaking {
        }
    }

    // A flight's rays start just off the block an item lies on and end just over the surface it lands on, so they don't
    // graze either.
    private static final double RAY_LIFT = 0.05;
    // Closer to the pad's centre than this along both axes, within half a block of its side, no arc comes over its edge
    // without a tall loop: the item hops instead.
    private static final double HOP_REACH = 1;
    // The main inventory; the hotbar (0 to 8), armour and off hand are what the player is using.
    private static final int FIRST_STORED = 9;
    private static final int LAST_STORED = 35;

    private final Plugin plugin;
    private final Supplier<PadSettings> settings;
    private final DeliveryService delivery;
    private final CustomItemResolver customItems;
    private final Map<ChunkKey, Map<BlockKey, PadEntry>> pads = new HashMap<>();
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
    public Placement placed(Player player, Block block, ItemStack hand) {
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
     * What breaking {@code block} does. When pads are made from items and the breaker {@code drops} items, the pad
     * gives its special item back, so it is kept while CraftEngine can't build that item (during /ce reload, or for
     * an ID it no longer knows).
     */
    public Breaking broken(Block block, boolean drops) {
        PadType type = registered(block);
        if (type == null) {
            return new Breaking.NotAPad();
        }
        ItemStack item = null;
        if (drops && settings.get().creation() == PadMode.ITEM) {
            item = createItem(type, 1);
            if (item == null) {
                return new Breaking.Kept();
            }
        }
        unregister(block);
        return new Breaking.Removed(item);
    }

    /** Whether {@code block} is the top block of a registered pad. */
    public boolean isPad(Block block) {
        return registered(block) != null;
    }

    /** The pad whose top block this is, if it is registered and its column is still complete. */
    public @Nullable PadType padAt(Block top) {
        PadType type = stored(top);
        return type != null && matches(read(top), read(top.getRelative(BlockFace.DOWN)), type) ? type : null;
    }

    /** The owner of the pad whose top block this is, or {@code null} when nobody owns it. */
    public @Nullable UUID ownerOf(Block top) {
        PadEntry entry = entry(top);
        return entry == null ? null : entry.owner();
    }

    /** A registered pad: its top block, its type and its owner, or {@code null} when nobody owns it. */
    public record Pad(Block top, PadType type, @Nullable UUID owner) {
    }

    /** The registered pads whose range reaches {@code center}, in its world. */
    public List<Pad> reaching(Block center) {
        BlockKey here = FrameGeometry.key(center);
        List<Pad> found = new ArrayList<>();
        for (Map<BlockKey, PadEntry> inChunk : pads.values()) {
            for (Map.Entry<BlockKey, PadEntry> pad : inChunk.entrySet()) {
                BlockKey key = pad.getKey();
                // A type taken out of pads.yml has no range until it is back.
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
                yield built == null ? null : ItemOverlay.apply(built, type.look());
            }
        };
        return item == null ? null : MarkerCodec.mark(item, Keys.PAD_ITEM, type.id());
    }

    public void tick() {
        tick++;
        if (tick % settings.get().sweepInterval() != 0) {
            return;
        }
        for (Map<BlockKey, PadEntry> inChunk : List.copyOf(pads.values())) {
            for (Map.Entry<BlockKey, PadEntry> pad : Map.copyOf(inChunk).entrySet()) {
                BlockKey key = pad.getKey();
                World world = plugin.getServer().getWorld(key.world());
                if (world != null) {
                    sweep(world.getBlockAt(key.x(), key.y(), key.z()), pad.getValue());
                }
            }
        }
    }

    private void sweep(Block top, PadEntry pad) {
        PadType type = settings.get().type(pad.type());
        // A type taken out of pads.yml, or mistyped, keeps its pads: they work again once it is back.
        if (type == null) {
            return;
        }
        // A player's break unregisters the pad at once; one by WorldEdit, a command, gravity or another block put in
        // its place is only seen here.
        PadBlock seen = read(top);
        if (!isTop(seen, type)) {
            unregister(top);
            return;
        }
        if (!matches(seen, read(top.getRelative(BlockFace.DOWN)), type)) {
            return;
        }
        // Items count when resting up to one block above the pad, like a hopper's pickup area.
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
            // The search also finds whoever brushes the space over the pad from beside it, and spectators, who crouch
            // to fly down.
            if (!player.isSneaking() || player.getGameMode() == GameMode.SPECTATOR
                    || !player.getLocation().getBlock().equals(top.getRelative(BlockFace.UP))) {
                continue;
            }
            PlayerInventory inventory = player.getInventory();
            List<Integer> filled = new ArrayList<>();
            for (int index = FIRST_STORED; index <= LAST_STORED; index++) {
                if (inventory.getItem(index) != null) {
                    filled.add(index);
                }
            }
            // One stack per check, at random, like a sorter's turn; one with nowhere to go doesn't hold up the rest.
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

    /** Throws the loose items around the pad onto it, so a later sweep sends them. */
    private void pull(Block top, int radius) {
        // Every pull lands on the pad's surface: the block right over the pad must leave room on top (empty, or a
        // carpet or slab on the pad), and the one above that must let the item come down through it.
        Block above = top.getRelative(BlockFace.UP);
        if (!above.isPassable() && above.getBoundingBox().getMaxY() >= above.getY() + 1
                || !top.getRelative(BlockFace.UP, 2).isPassable()) {
            return;
        }
        Location surface = top.getLocation().add(0.5, 0, 0.5);
        surface.setY(above.isPassable() ? above.getY() : above.getBoundingBox().getMaxY());
        // A lying item is slowed by the block just under a block's height below it: the pad's top, also under a carpet
        // or slab.
        double grip = Pull.grip(top.getType().getSlipperiness());
        for (Item item : top.getWorld().getNearbyEntitiesByType(Item.class, surface, radius, radius, radius)) {
            Location from = item.getLocation();
            // Handlers of an earlier PadPullEvent may remove items. One in the air is already on its way, and so is one
            // another pad threw this sweep: it lies on the ground until its own tick. One over or under this pad is on
            // it or can't get there, and one on another pad goes with that pad's sweep.
            if (!item.isValid() || !item.isOnGround() || item.getVelocity().getY() > 0 || DropCodec.isMarked(item)
                    || from.distanceSquared(surface) > (double) radius * radius
                    || Math.abs(from.getX() - surface.getX()) < 0.5 && Math.abs(from.getZ() - surface.getZ()) < 0.5
                    || isPad(from.getBlock().getRelative(BlockFace.DOWN))) {
                continue;
            }
            if (Math.max(Math.abs(surface.getX() - from.getX()), Math.abs(surface.getZ() - from.getZ())) < HOP_REACH) {
                hop(item, top, surface, grip);
            } else {
                throwOnto(item, top, surface, grip);
            }
        }
    }

    // Rays through the points of the flight: they meet a wall in the way, or a roof over its top. They pass under the
    // curve between those points, so a low overhang there can still catch the item, which then lands elsewhere and is
    // tried again on the next check.
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
        // Worked out when it runs, from where the item is then, so a tick before or after its highest point makes no
        // difference. Harmless if FrameSort is gone by then, so the task isn't kept.
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

    // While /ce reload has CraftEngine's blocks unbound, a pad's CraftEngine top block can't be told from another:
    // the pad is kept, and protected, until the reload ends.
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

    // A CraftEngine block shows to the server as a vanilla one (bricks by default), so it never counts as that block;
    // while /ce reload has it unbound, it counts as no block at all.
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
        public Item moveTo(Location destination) {
            item.teleport(destination);
            item.setVelocity(new Vector());
            return item;
        }

        @Override
        public void destroy() {
            item.remove();
        }
    }
}
