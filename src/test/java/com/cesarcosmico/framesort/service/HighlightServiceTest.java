package com.cesarcosmico.framesort.service;

import org.bukkit.block.BlockFace;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HighlightServiceTest {

    private static final float EPSILON = 1e-5f;
    private static final BlockFace[] FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST, BlockFace.WEST};

    @Test
    void spriteSitsJustInFrontOfTheFrameOnEveryFace() {
        for (BlockFace facing : FACES) {
            Vector3f centre = HighlightService.frameTransform(facing).transformPosition(new Vector3f());
            assertEquals(facing.getModX() * HighlightService.FRAME_OFFSET, centre.x, EPSILON, facing + " x");
            assertEquals(facing.getModY() * HighlightService.FRAME_OFFSET, centre.y, EPSILON, facing + " y");
            assertEquals(facing.getModZ() * HighlightService.FRAME_OFFSET, centre.z, EPSILON, facing + " z");
        }
    }

    @Test
    void spriteLiesFlatAgainstTheWall() {
        for (BlockFace facing : FACES) {
            // A flat item lies in its local XY plane, so its local Z axis must point along the frame's facing.
            Vector3f normal = HighlightService.frameTransform(facing).transformDirection(new Vector3f(0, 0, 1));
            assertEquals(1f, Math.abs(normal.dot(facing.getModX(), facing.getModY(), facing.getModZ())), EPSILON,
                    facing.toString());
        }
    }
}
