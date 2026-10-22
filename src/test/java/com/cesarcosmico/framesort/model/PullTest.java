package com.cesarcosmico.framesort.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class PullTest {

    // Steps the flight as the game does: gravity, then the move, then the drag.
    private static double[] landing(double dx, double dy, double dz) {
        Pull.Velocity velocity = Pull.velocity(dx, dy, dz);
        double vx = velocity.x();
        double vy = velocity.y();
        double vz = velocity.z();
        double x = 0;
        double y = 0;
        double z = 0;
        for (int tick = 0; tick < Pull.FLIGHT_TICKS; tick++) {
            vy -= Pull.GRAVITY;
            x += vx;
            y += vy;
            z += vz;
            vx *= Pull.DRAG;
            vy *= Pull.DRAG;
            vz *= Pull.DRAG;
        }
        return new double[]{x, y, z};
    }

    @Test
    void landsOnTheTargetAcrossTheWholeRadius() {
        assertArrayEquals(new double[]{1, 0, 0}, landing(1, 0, 0), 1e-9);
        assertArrayEquals(new double[]{-16, 0, 11}, landing(-16, 0, 11), 1e-9);
    }

    @Test
    void landsOnATargetAboveOrBelow() {
        assertArrayEquals(new double[]{3, 2, -1}, landing(3, 2, -1), 1e-9);
        assertArrayEquals(new double[]{0, -4, 2}, landing(0, -4, 2), 1e-9);
    }

    @Test
    void anItemAlreadyThereHopsInPlace() {
        assertArrayEquals(new double[]{0, 0, 0}, landing(0, 0, 0), 1e-9);
    }
}
