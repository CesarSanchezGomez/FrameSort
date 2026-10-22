package com.cesarcosmico.framesort.model;

import org.jspecify.annotations.Nullable;

/**
 * The starting velocity that carries an item to a point in {@link #FLIGHT_TICKS}, under the game's item physics: each
 * tick in the air the vertical speed drops by {@link #GRAVITY}, the item moves, and its speed is multiplied by
 * {@link #DRAG}. Over the flight, one block per tick of starting speed carries the item the sum of DRAG^k blocks, and
 * gravity pulls it down by an amount the vertical speed makes up for. Distances are in blocks, speeds in blocks per
 * tick.
 */
public final class Pull {

    public record Velocity(double x, double y, double z) {
    }

    // ItemEntity#getDefaultGravity and Entity#getAirDrag; they change with Minecraft, not with Paper.
    static final double GRAVITY = 0.04;
    static final double DRAG = 0.98;
    // Half a second: long enough to see the arc.
    public static final int FLIGHT_TICKS = 10;
    private static final double REACH = (1 - Math.pow(DRAG, FLIGHT_TICKS)) / (1 - DRAG);
    private static final double FALL = GRAVITY / (1 - DRAG) * (FLIGHT_TICKS - DRAG * REACH);

    private Pull() {
    }

    /**
     * The velocity, or {@code null} when the target is so far below that the item couldn't leave the ground: with no
     * more upward speed than one tick of gravity takes away, a lying item never takes off.
     */
    public static @Nullable Velocity velocity(double dx, double dy, double dz) {
        double up = (dy + FALL) / REACH;
        return up <= GRAVITY ? null : new Velocity(dx / REACH, up, dz / REACH);
    }
}
