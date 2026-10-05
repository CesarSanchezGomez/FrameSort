package com.cesarcosmico.framesort.model;

import org.bukkit.block.BlockFace;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * Where a target frame hangs on its container, relative to the way the container faces. Left and right are as
 * seen by a player standing in front of the container, looking at it.
 */
public enum FramePosition {
    TOP,
    BOTTOM,
    FRONT,
    BACK,
    LEFT,
    RIGHT;

    private static final Set<FramePosition> ANY_SIDE = Set.of(FRONT, BACK, LEFT, RIGHT);

    /**
     * The positions a frame on {@code face} counts as.
     *
     * @param face   the container face the frame hangs on
     * @param facing the container's facing, or {@code null} when it has none; without a horizontal facing (a hopper,
     *               an upright barrel) a side face has no front or back, so it counts as every side
     */
    public static Set<FramePosition> of(BlockFace face, @Nullable BlockFace facing) {
        return switch (face) {
            case UP -> Set.of(TOP);
            case DOWN -> Set.of(BOTTOM);
            default -> {
                if (facing == null || !isHorizontal(facing)) {
                    yield ANY_SIDE;
                }
                if (face == facing) {
                    yield Set.of(FRONT);
                }
                if (face == facing.getOppositeFace()) {
                    yield Set.of(BACK);
                }
                yield face == clockwise(facing) ? Set.of(LEFT) : Set.of(RIGHT);
            }
        };
    }

    private static boolean isHorizontal(BlockFace face) {
        return face == BlockFace.NORTH || face == BlockFace.SOUTH || face == BlockFace.EAST || face == BlockFace.WEST;
    }

    // Facing the container's front means looking the other way, so its clockwise neighbour is on the viewer's left.
    private static BlockFace clockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
    }
}
