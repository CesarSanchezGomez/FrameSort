package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.item.FrameCodec;
import com.cesarcosmico.framesort.model.BlockKey;
import com.cesarcosmico.framesort.model.ChunkKey;
import org.bukkit.World;
import org.bukkit.entity.ItemFrame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class TargetIndex {

    public static final String MARK_PERMISSION = "framesort.target.create";

    private final Supplier<FrameSortSettings> settings;
    public record Near(ItemFrame frame, long distanceSquared) {
    }

    private final Map<ChunkKey, Map<ItemFrame, BlockKey>> frames = new HashMap<>();
    private final Map<UUID, Long> epochs = new HashMap<>();
    // A change of trust can change any world's matches, even one with no epoch yet.
    private long epochEverywhere;

    public TargetIndex(Supplier<FrameSortSettings> settings) {
        this.settings = settings;
    }

    public void add(ItemFrame frame) {
        BlockKey position = position(frame);
        if (qualifies(frame) && frames.computeIfAbsent(position.chunk(), key -> new HashMap<>())
                .putIfAbsent(frame, position) == null) {
            changed(frame.getWorld());
        }
    }

    public void remove(ItemFrame frame) {
        ChunkKey chunk = position(frame).chunk();
        Map<ItemFrame, BlockKey> inChunk = frames.get(chunk);
        if (inChunk != null && inChunk.remove(frame) != null) {
            if (inChunk.isEmpty()) {
                frames.remove(chunk);
            }
            changed(frame.getWorld());
        }
    }

    private void changed(World world) {
        epochs.merge(world.getUID(), 1L, Long::sum);
    }

    public void changedEverywhere() {
        epochEverywhere++;
    }

    public long epoch(UUID world) {
        return epochs.getOrDefault(world, 0L) + epochEverywhere;
    }

    public boolean isMarked(ItemFrame frame) {
        return FrameCodec.isMarked(frame);
    }

    public void mark(ItemFrame frame, UUID owner) {
        FrameCodec.setOwner(frame, owner);
        FrameCodec.setMarked(frame, true);
        reindex(frame);
    }

    public void unmark(ItemFrame frame) {
        FrameCodec.clearOwner(frame);
        FrameCodec.setMarked(frame, false);
        reindex(frame);
    }

    public void unmarkForNewItem(ItemFrame frame) {
        if (isMarked(frame)) {
            unmark(frame);
        }
    }

    // Removing and adding again always moves the frame in or out of the index, or out and back in, so the epoch rises.
    private void reindex(ItemFrame frame) {
        remove(frame);
        add(frame);
    }

    public boolean isTarget(ItemFrame frame) {
        Map<ItemFrame, BlockKey> inChunk = frames.get(position(frame).chunk());
        return inChunk != null && inChunk.containsKey(frame);
    }

    public List<Near> near(BlockKey center, int radius) {
        long radiusSquared = (long) radius * radius;
        List<Near> found = new ArrayList<>();
        for (int cx = (center.x() - radius) >> 4; cx <= (center.x() + radius) >> 4; cx++) {
            for (int cz = (center.z() - radius) >> 4; cz <= (center.z() + radius) >> 4; cz++) {
                Map<ItemFrame, BlockKey> inChunk = frames.get(new ChunkKey(center.world(), cx, cz));
                if (inChunk == null) {
                    continue;
                }
                for (Map.Entry<ItemFrame, BlockKey> frame : inChunk.entrySet()) {
                    long distanceSquared = frame.getValue().distanceSquared(center);
                    if (distanceSquared <= radiusSquared) {
                        found.add(new Near(frame.getKey(), distanceSquared));
                    }
                }
            }
        }
        return List.copyOf(found);
    }

    public void clear() {
        frames.clear();
        epochs.replaceAll((world, epoch) -> epoch + 1);
    }

    public boolean markable(ItemFrame frame) {
        return settings.get().targets().frameTypes().contains(frame.getType());
    }

    private boolean qualifies(ItemFrame frame) {
        return markable(frame) && isMarked(frame);
    }

    private static BlockKey position(ItemFrame frame) {
        return FrameGeometry.key(frame.getLocation().getBlock());
    }
}
