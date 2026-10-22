package com.cesarcosmico.framesort.model;

/**
 * The starting velocity that throws an item onto a point in {@link #FLIGHT_TICKS}, under the game's item physics:
 * each tick in the air the vertical speed drops by {@link #GRAVITY}, the item moves, and its speed is multiplied by
 * {@link #DRAG}. Distances are in blocks and speeds in blocks per tick.
 */
public final class Pull {

    public record Velocity(double x, double y, double z) {
    }

    // ItemEntity#getDefaultGravity and Entity#getAirDrag; they change with Minecraft, not with Paper.
    static final double GRAVITY = 0.04;
    static final double DRAG = 0.98;
    // Long enough for a visible arc, short enough to land before most pads check again.
    static final int FLIGHT_TICKS = 10;

    private Pull() {
    }

    public static Velocity velocity(double dx, double dy, double dz) {
        // How far one block per tick of starting speed carries the item: the sum of DRAG^k over the flight.
        double reach = (1 - Math.pow(DRAG, FLIGHT_TICKS)) / (1 - DRAG);
        // How far gravity pulls it down over the flight, which the vertical speed makes up for.
        double fall = GRAVITY / (1 - DRAG) * (FLIGHT_TICKS - DRAG * reach);
        return new Velocity(dx / reach, (dy + fall) / reach, dz / reach);
    }
}
