package com.cesarcosmico.framesort.model;

import java.util.UUID;

/** A block position that is safe to use as a map key and to keep after the chunk unloads. */
public record BlockKey(UUID world, int x, int y, int z) {

    public ChunkKey chunk() {
        return new ChunkKey(world, x >> 4, z >> 4);
    }

    public long distanceSquared(BlockKey other) {
        long dx = x - other.x;
        long dy = y - other.y;
        long dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
