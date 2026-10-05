package com.cesarcosmico.framesort.model;

import org.bukkit.block.BlockFace;
import org.jspecify.annotations.Nullable;

/** Where a target frame hangs on its container, relative to the way the container faces. */
public enum FramePosition {
    TOP,
    BOTTOM,
    FRONT,
    BACK,
    SIDES;

    /**
     * Classifies a frame.
     *
     * @param face   the container face the frame hangs on
     * @param facing the container's facing, or {@code null} when it has none; a vertical facing (an upright
     *               barrel) leaves no front or back, so every horizontal face counts as {@link #SIDES}
     */
    public static FramePosition of(BlockFace face, @Nullable BlockFace facing) {
        return switch (face) {
            case UP -> TOP;
            case DOWN -> BOTTOM;
            default -> {
                if (facing == null || !isHorizontal(facing)) {
                    yield SIDES;
                }
                if (face == facing) {
                    yield FRONT;
                }
                yield face == facing.getOppositeFace() ? BACK : SIDES;
            }
        };
    }

    private static boolean isHorizontal(BlockFace face) {
        return face == BlockFace.NORTH || face == BlockFace.SOUTH || face == BlockFace.EAST || face == BlockFace.WEST;
    }
}
