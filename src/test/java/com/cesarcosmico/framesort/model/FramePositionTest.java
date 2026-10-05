package com.cesarcosmico.framesort.model;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FramePositionTest {

    @Test
    void verticalFacesIgnoreTheFacing() {
        assertEquals(Set.of(FramePosition.TOP), FramePosition.of(BlockFace.UP, BlockFace.NORTH));
        assertEquals(Set.of(FramePosition.BOTTOM), FramePosition.of(BlockFace.DOWN, null));
    }

    @Test
    void chestFacingNorthAsSeenFromItsFront() {
        // Standing north of the chest and looking south, east is on the player's left.
        assertEquals(Set.of(FramePosition.FRONT), FramePosition.of(BlockFace.NORTH, BlockFace.NORTH));
        assertEquals(Set.of(FramePosition.BACK), FramePosition.of(BlockFace.SOUTH, BlockFace.NORTH));
        assertEquals(Set.of(FramePosition.LEFT), FramePosition.of(BlockFace.EAST, BlockFace.NORTH));
        assertEquals(Set.of(FramePosition.RIGHT), FramePosition.of(BlockFace.WEST, BlockFace.NORTH));
    }

    @Test
    void chestFacingEast() {
        assertEquals(Set.of(FramePosition.LEFT), FramePosition.of(BlockFace.SOUTH, BlockFace.EAST));
        assertEquals(Set.of(FramePosition.RIGHT), FramePosition.of(BlockFace.NORTH, BlockFace.EAST));
    }

    @Test
    void noHorizontalFacingMeansAnySide() {
        Set<FramePosition> anySide = Set.of(FramePosition.FRONT, FramePosition.BACK, FramePosition.LEFT,
                FramePosition.RIGHT);
        assertEquals(anySide, FramePosition.of(BlockFace.SOUTH, null));
        assertEquals(anySide, FramePosition.of(BlockFace.SOUTH, BlockFace.UP));
    }
}
