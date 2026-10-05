package com.cesarcosmico.framesort.model;

import java.util.UUID;

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
