package com.cesarcosmico.framesort.model;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FramePositionTest {

    @Test
    void verticalFacesIgnoreTheFacing() {
        assertEquals(FramePosition.TOP, FramePosition.of(BlockFace.UP, BlockFace.NORTH));
        assertEquals(FramePosition.BOTTOM, FramePosition.of(BlockFace.DOWN, null));
    }

    @Test
    void horizontalFacesAreRelativeToTheFacing() {
        assertEquals(FramePosition.FRONT, FramePosition.of(BlockFace.EAST, BlockFace.EAST));
        assertEquals(FramePosition.BACK, FramePosition.of(BlockFace.WEST, BlockFace.EAST));
        assertEquals(FramePosition.SIDES, FramePosition.of(BlockFace.NORTH, BlockFace.EAST));
    }

    @Test
    void noHorizontalFacingMeansSides() {
        assertEquals(FramePosition.SIDES, FramePosition.of(BlockFace.SOUTH, null));
        assertEquals(FramePosition.SIDES, FramePosition.of(BlockFace.SOUTH, BlockFace.UP));
    }
}
