package com.cesarcosmico.framesort.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PullTest {

    // A pad's top surface is at y 0 and its centre at x 0, and an item is this wide.
    private static final double PAD_HALF_WIDTH = 0.5;
    private static final double ITEM_HALF_WIDTH = 0.125;

    // Steps the flight as the game does: gravity, then the move, then the drag. Returns where the item is after each
    // tick, as {x, y, z}.
    private static double[][] flight(double dx, double dy, double dz) {
        Pull.Velocity velocity = Pull.velocity(dx, dy, dz);
        assertNotNull(velocity);
        double vx = velocity.x();
        double vy = velocity.y();
        double vz = velocity.z();
        double x = 0;
        double y = 0;
        double z = 0;
        double[][] path = new double[Pull.FLIGHT_TICKS][];
        for (int tick = 0; tick < Pull.FLIGHT_TICKS; tick++) {
            vy -= Pull.GRAVITY;
            x += vx;
            y += vy;
            z += vz;
            vx *= Pull.DRAG;
            vy *= Pull.DRAG;
            vz *= Pull.DRAG;
            path[tick] = new double[]{x, y, z};
        }
        return path;
    }

    private static double[] landing(double dx, double dy, double dz) {
        double[][] path = flight(dx, dy, dz);
        return path[path.length - 1];
    }

    @Test
    void landsOnTheTargetAcrossTheWholeRadius() {
        assertArrayEquals(new double[]{1, 0, 0}, landing(1, 0, 0), 1e-9);
        assertArrayEquals(new double[]{-16, 0, 11}, landing(-16, 0, 11), 1e-9);
    }

    @Test
    void landsOnATargetAboveOrBelow() {
        assertArrayEquals(new double[]{3, 2, -1}, landing(3, 2, -1), 1e-9);
        assertArrayEquals(new double[]{0, -1.5, 2}, landing(0, -1.5, 2), 1e-9);
    }

    @Test
    void anItemAlreadyThereHopsInPlace() {
        assertArrayEquals(new double[]{0, 0, 0}, landing(0, 0, 0), 1e-9);
    }

    // An item at the foot of a pad goes straight up to the height of the point over the pad.
    @Test
    void aHopReachesTheHeightStraightUp() {
        assertArrayEquals(new double[]{0, 3.5, 0}, landing(0, 3.5, 0), 1e-9);
    }

    @Test
    void aTargetFarBelowCantBeReachedFromTheGround() {
        assertNull(Pull.velocity(2, -3.5, 0));
    }

    // From the ground beside a two-block pad, level with its top, or from a ledge above it, the arc passes over the
    // pad's edge instead of hitting its side.
    @Test
    void theArcClearsThePadsEdge() {
        for (double startY : new double[]{-2, 0, 3}) {
            for (double distance : new double[]{1.25, 2, 4, 8, 16}) {
                for (double[] at : flight(-distance, Pull.AIM_ABOVE - startY, 0)) {
                    double x = distance + at[0];
                    double y = startY + at[1];
                    if (Math.abs(x) <= PAD_HALF_WIDTH + ITEM_HALF_WIDTH) {
                        assertTrue(y >= 0, "from " + distance + " at height " + startY + ", over the edge at " + y);
                    }
                }
            }
        }
    }
}
