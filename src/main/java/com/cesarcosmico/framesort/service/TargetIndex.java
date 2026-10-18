package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.config.TargetSettings;
import com.cesarcosmico.framesort.item.FrameCodec;
import com.cesarcosmico.framesort.model.BlockKey;
import com.cesarcosmico.framesort.model.ChunkKey;
import com.cesarcosmico.framesort.model.TargetRegistration;
import org.bukkit.World;
import org.bukkit.entity.ItemFrame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Indexed by chunk so a lookup only scans the chunks in range; the per-world epoch invalidates cached matches. */
public final class TargetIndex {

    private final Supplier<FrameSortSettings> settings;
    private final Map<ChunkKey, Set<ItemFrame>> frames = new HashMap<>();
    private final Map<UUID, Long> epochs = new HashMap<>();
    // Added to every world's epoch: a change of trust can change any world's matches, even one with no epoch yet.
    private long everywhere;

    public TargetIndex(Supplier<FrameSortSettings> settings) {
        this.settings = settings;
    }

    public void add(ItemFrame frame) {
        if (qualifies(frame) && frames.computeIfAbsent(chunk(frame), key -> new HashSet<>()).add(frame)) {
            changed(frame.getWorld());
        }
    }

    public void remove(ItemFrame frame) {
        ChunkKey chunk = chunk(frame);
        Set<ItemFrame> inChunk = frames.get(chunk);
        if (inChunk != null && inChunk.remove(frame)) {
            if (inChunk.isEmpty()) {
                frames.remove(chunk);
            }
            changed(frame.getWorld());
        }
    }

    public void changed(World world) {
        epochs.merge(world.getUID(), 1L, Long::sum);
    }

    public void changedEverywhere() {
        everywhere++;
    }

    public long epoch(UUID world) {
        return epochs.getOrDefault(world, 0L) + everywhere;
    }

    public boolean isMarked(ItemFrame frame) {
        return FrameCodec.isMarked(frame);
    }

    /** A marked frame belongs to whoever marked it. */
    public void mark(ItemFrame frame, UUID owner) {
        FrameCodec.setOwner(frame, owner);
        FrameCodec.setMarked(frame, true);
        reindex(frame);
    }

    /** Unmarking frees the frame, so anyone may mark it next. */
    public void unmark(ItemFrame frame) {
        FrameCodec.clearOwner(frame);
        FrameCodec.setMarked(frame, false);
        reindex(frame);
    }

    /** With automatic registration nothing is marked, so the frame belongs to whoever hung it. */
    public void hung(ItemFrame frame, UUID owner) {
        if (settings.get().targets().registration() == TargetRegistration.AUTOMATIC) {
            FrameCodec.setOwner(frame, owner);
            changed(frame.getWorld());
        }
    }

    private void reindex(ItemFrame frame) {
        remove(frame);
        add(frame);
        // A frame that stays indexed (automatic registration) changes owner without entering or leaving.
        changed(frame.getWorld());
    }

    public boolean isTarget(ItemFrame frame) {
        Set<ItemFrame> inChunk = frames.get(chunk(frame));
        return inChunk != null && inChunk.contains(frame);
    }

    public List<ItemFrame> near(BlockKey center, int radius) {
        long radiusSquared = (long) radius * radius;
        List<ItemFrame> found = new ArrayList<>();
        for (int cx = (center.x() - radius) >> 4; cx <= (center.x() + radius) >> 4; cx++) {
            for (int cz = (center.z() - radius) >> 4; cz <= (center.z() + radius) >> 4; cz++) {
                Set<ItemFrame> inChunk = frames.get(new ChunkKey(center.world(), cx, cz));
                if (inChunk == null) {
                    continue;
                }
                for (ItemFrame frame : inChunk) {
                    if (FrameGeometry.key(frame.getLocation().getBlock()).distanceSquared(center) <= radiusSquared) {
                        found.add(frame);
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

    private boolean qualifies(ItemFrame frame) {
        TargetSettings targets = settings.get().targets();
        return targets.frameTypes().contains(frame.getType())
                && (targets.registration() == TargetRegistration.AUTOMATIC || isMarked(frame));
    }

    private static ChunkKey chunk(ItemFrame frame) {
        return FrameGeometry.key(frame.getLocation().getBlock()).chunk();
    }
}
