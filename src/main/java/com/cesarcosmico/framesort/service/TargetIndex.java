package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.config.FrameSortSettings;
import com.cesarcosmico.framesort.config.TargetSettings;
import com.cesarcosmico.framesort.item.Keys;
import com.cesarcosmico.framesort.model.BlockKey;
import com.cesarcosmico.framesort.model.ChunkKey;
import com.cesarcosmico.framesort.model.TargetRegistration;
import org.bukkit.World;
import org.bukkit.entity.ItemFrame;
import org.bukkit.persistence.PersistentDataType;

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

    public long epoch(UUID world) {
        return epochs.getOrDefault(world, 0L);
    }

    public boolean isMarked(ItemFrame frame) {
        return frame.getPersistentDataContainer().has(Keys.TARGET);
    }

    public void setMarked(ItemFrame frame, UUID by, boolean marked) {
        if (marked) {
            frame.getPersistentDataContainer().set(Keys.TARGET, PersistentDataType.STRING, by.toString());
        } else {
            frame.getPersistentDataContainer().remove(Keys.TARGET);
        }
        remove(frame);
        add(frame);
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
                    if (frame.isValid()
                            && FrameGeometry.key(frame.getLocation().getBlock()).distanceSquared(center) <= radiusSquared) {
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
