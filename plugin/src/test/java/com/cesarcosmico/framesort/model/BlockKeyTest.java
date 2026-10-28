package com.cesarcosmico.framesort.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockKeyTest {

    private static final UUID OVERWORLD = UUID.randomUUID();
    private static final UUID NETHER = UUID.randomUUID();

    @Test
    void distanceIsSquaredInTheSameWorld() {
        assertEquals(14, new BlockKey(OVERWORLD, 1, 2, 3).distanceSquared(new BlockKey(OVERWORLD, 0, 0, 0)));
    }

    @Test
    void otherWorldsAreNeverInRange() {
        assertEquals(Long.MAX_VALUE, new BlockKey(OVERWORLD, 0, 0, 0).distanceSquared(new BlockKey(NETHER, 0, 0, 0)));
    }
}
