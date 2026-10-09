package com.cesarcosmico.framesort.service;

import org.bukkit.block.BlockFace;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HighlightServiceTest {

    private static final float EPSILON = 1e-5f;

    @Test
    void itemCentreSitsJustInFrontOfThePlateOnEveryFace() {
        for (BlockFace facing : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST}) {
            for (int rotation = 0; rotation < 8; rotation++) {
                Vector3f centre = HighlightService.itemTransform(facing, rotation, true)
                        .transformPosition(new Vector3f());
                // 0.46875 out to the block centre, then 0.4375 back towards the wall: 1/32 in front of the entity.
                assertEquals(facing.getModX() / 32f, centre.x, EPSILON, facing + " x");
                assertEquals(facing.getModY() / 32f, centre.y, EPSILON, facing + " y");
                assertEquals(facing.getModZ() / 32f, centre.z, EPSILON, facing + " z");
            }
        }
    }

    @Test
    void invisibleFramesDrawTheItemAgainstTheWall() {
        Vector3f centre = HighlightService.itemTransform(BlockFace.SOUTH, 0, false).transformPosition(new Vector3f());
        assertEquals(-1 / 32f, centre.z, EPSILON);
    }
}
