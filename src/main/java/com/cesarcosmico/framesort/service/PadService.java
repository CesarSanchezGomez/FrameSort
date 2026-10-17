package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.config.PadSettings;
import com.cesarcosmico.framesort.config.PadType;
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
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    private final Server server;
    private final Supplier<PadSettings> settings;
    private final DeliveryService delivery;
    private final CustomItemResolver customItems;
    private final Map<ChunkKey, Map<BlockKey, PadEntry>> pads = new HashMap<>();
    private long tick;

    public PadService(Server server, Supplier<PadSettings> settings, DeliveryService delivery,
                      CustomItemResolver customItems) {
        this.server = server;
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

    /** The top blocks of the registered pads within {@code radius} of {@code center}, in its world. */
    public List<Block> near(Block center, int radius) {
        BlockKey here = FrameGeometry.key(center);
        long radiusSquared = (long) radius * radius;
        List<Block> found = new ArrayList<>();
        for (Map<BlockKey, PadEntry> inChunk : pads.values()) {
            for (BlockKey key : inChunk.keySet()) {
                if (key.distanceSquared(here) <= radiusSquared) {
                    found.add(center.getWorld().getBlockAt(key.x(), key.y(), key.z()));
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
                World world = server.getWorld(key.world());
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
                delivery.deliver(top, pad.owner(), new EntitySource(item));
            }
        }
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
        public Location location() {
            return item.getLocation();
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
        public void moveTo(Location destination) {
            item.teleport(destination);
            item.setVelocity(new Vector());
        }

        @Override
        public void destroy() {
            item.remove();
        }
    }
}
