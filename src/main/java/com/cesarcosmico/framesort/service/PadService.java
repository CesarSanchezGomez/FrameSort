package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.config.PadSettings;
import com.cesarcosmico.framesort.config.PadType;
import com.cesarcosmico.framesort.item.ItemTemplate;
import com.cesarcosmico.framesort.item.Keys;
import com.cesarcosmico.framesort.item.PadCodec;
import com.cesarcosmico.framesort.model.BlockKey;
import com.cesarcosmico.framesort.model.ChunkKey;
import com.cesarcosmico.framesort.model.PadMode;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
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

    private final Server server;
    private final Supplier<PadSettings> settings;
    private final DeliveryService delivery;
    private final Map<ChunkKey, Map<BlockKey, String>> pads = new HashMap<>();
    private long tick;

    public PadService(Server server, Supplier<PadSettings> settings, DeliveryService delivery) {
        this.server = server;
        this.settings = settings;
        this.delivery = delivery;
    }

    public void load(Chunk chunk) {
        Map<BlockKey, String> stored = PadCodec.read(chunk);
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
        String marker = ItemTemplate.marker(hand, Keys.PAD_ITEM);
        boolean blocked = false;
        for (PadType type : current.types().values()) {
            if (type.top() != block.getType() || !matches(block, type)) {
                continue;
            }
            boolean allowed = switch (current.creation()) {
                case ANYONE -> true;
                case PERMISSION -> player.hasPermission(CREATE_PERMISSION);
                case ITEM -> type.id().equals(marker);
            };
            if (allowed) {
                register(block, type);
                return new Placement.Created(type);
            }
            blocked = true;
        }
        // In item mode a plain block is just a block: building with it is not an error worth a message.
        boolean silent = current.creation() == PadMode.ITEM && marker == null;
        return blocked && !silent ? new Placement.NotAllowed() : new Placement.NotAPad();
    }

    /** Returns the special item to drop instead of the block when pads are made from items, or {@code null}. */
    public @Nullable ItemStack broken(Block block) {
        String typeId = unregister(block);
        if (typeId == null || settings.get().creation() != PadMode.ITEM) {
            return null;
        }
        PadType type = settings.get().type(typeId);
        return type == null ? null : createItem(type, 1);
    }

    public boolean isRegistered(Block block) {
        Map<BlockKey, String> inChunk = pads.get(FrameGeometry.key(block.getChunk()));
        return inChunk != null && inChunk.containsKey(FrameGeometry.key(block));
    }

    /** The pad whose top block this is, if it is registered and its column is still complete. */
    public @Nullable PadType padAt(Block top) {
        Map<BlockKey, String> inChunk = pads.get(FrameGeometry.key(top.getChunk()));
        String typeId = inChunk == null ? null : inChunk.get(FrameGeometry.key(top));
        PadType type = typeId == null ? null : settings.get().type(typeId);
        return type != null && matches(top, type) ? type : null;
    }

    public List<Block> near(BlockKey center, int radius) {
        List<Block> found = new ArrayList<>();
        World world = server.getWorld(center.world());
        if (world == null) {
            return List.of();
        }
        long radiusSquared = (long) radius * radius;
        for (Map<BlockKey, String> inChunk : pads.values()) {
            for (BlockKey key : inChunk.keySet()) {
                if (key.distanceSquared(center) <= radiusSquared) {
                    found.add(world.getBlockAt(key.x(), key.y(), key.z()));
                }
            }
        }
        return List.copyOf(found);
    }

    public ItemStack createItem(PadType type, int amount) {
        return type.item().create(Keys.PAD_ITEM, type.id(), amount);
    }

    public void tick() {
        tick++;
        if (tick % settings.get().sweepInterval() != 0) {
            return;
        }
        for (Map<BlockKey, String> inChunk : List.copyOf(pads.values())) {
            for (BlockKey key : List.copyOf(inChunk.keySet())) {
                World world = server.getWorld(key.world());
                if (world != null) {
                    sweep(world.getBlockAt(key.x(), key.y(), key.z()));
                }
            }
        }
    }

    private void sweep(Block top) {
        if (padAt(top) == null) {
            return;
        }
        // Items count when resting up to one block above the pad, like a hopper's pickup area.
        Location above = top.getLocation().add(0.5, 1.5, 0.5);
        for (Item item : top.getWorld().getNearbyEntitiesByType(Item.class, above, 0.5, 0.5, 0.5)) {
            if (item.isValid()) {
                delivery.deliver(top, new EntitySource(item));
            }
        }
    }

    private static boolean matches(Block top, PadType type) {
        for (int depth = 0; depth < type.structure().size(); depth++) {
            if (top.getRelative(0, -depth, 0).getType() != type.structure().get(depth)) {
                return false;
            }
        }
        return true;
    }

    private void register(Block block, PadType type) {
        Map<BlockKey, String> inChunk = pads.computeIfAbsent(FrameGeometry.key(block.getChunk()),
                key -> new LinkedHashMap<>());
        inChunk.put(FrameGeometry.key(block), type.id());
        PadCodec.write(block.getChunk(), inChunk);
    }

    private @Nullable String unregister(Block block) {
        ChunkKey chunk = FrameGeometry.key(block.getChunk());
        Map<BlockKey, String> inChunk = pads.get(chunk);
        String typeId = inChunk == null ? null : inChunk.remove(FrameGeometry.key(block));
        if (typeId != null) {
            PadCodec.write(block.getChunk(), inChunk);
            if (inChunk.isEmpty()) {
                pads.remove(chunk);
            }
        }
        return typeId;
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
