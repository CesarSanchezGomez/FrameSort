package com.cesarcosmico.framesort.model;

import java.util.UUID;

public record BlockKey(UUID world, int x, int y, int z) {

    public ChunkKey chunk() {
        return new ChunkKey(world, x >> 4, z >> 4);
    }

    /** Blocks in different worlds are never in range of each other: their distance is {@link Long#MAX_VALUE}. */
    public long distanceSquared(BlockKey other) {
        if (!world.equals(other.world)) {
            return Long.MAX_VALUE;
        }
        long dx = x - other.x;
        long dy = y - other.y;
        long dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
