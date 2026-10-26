package com.cesarcosmico.framesort.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PullTest {

    // Most blocks, and ice.
    private static final double GRIP = Pull.grip(0.6);
    private static final double ICE_GRIP = Pull.grip(0.98);
    // How close to the pad's centre a pulled item has to stop.
    private static final double ON_CENTRE = 0.05;
    private static final int SETTLE_TICKS = 300;
    // Items lying on the ground at the foot of a two-block pad, one block lower, level with its surface, and on a ledge
    // above it.
    private static final double[] HEIGHTS = {-2, -1, 0, 3};
    // Along an axis, close to an axis, diagonal, and at an odd angle.
    private static final double[][] DIRECTIONS = {{1, 0}, {0.96, 0.28}, {Math.sqrt(0.5), -Math.sqrt(0.5)}, {-0.6, 0.8}};

    /** The pad's top at height {@code surface}, its centre at ({@code x}, {@code z}), from where the item starts. */
    private record Pad(double x, double surface, double z) {
        // The item's box overlaps the pad, seen from above.
        boolean under(double itemX, double itemZ) {
            return Math.max(Math.abs(x - itemX), Math.abs(z - itemZ)) < Pull.EDGE;
        }
    }

    // Steps an item as ItemEntity#tick and Paper's collisions do: gravity; a lying item with almost no sideways speed
    // skips three ticks in four; the move, up or down first and then sideways, which stops on the pad's top without a
    // bounce; then the ground's grip, or the air's drag, on the sideways speed. It fails if the item falls past the
    // pad's top, or hits its side while `strict`; otherwise the side stops it sideways, as a bounciness of 0 does.
    private static final class Flight {
        private final Pad pad;
        private boolean strict = true;
        private double x;
        private double y;
        private double z;
        private double vx;
        private double vy;
        private double vz;
        private boolean grounded;
        private int tick;
        private double apex;
        private int landed;

        Flight(Pad pad, Pull.Velocity velocity, boolean lying) {
            this.pad = pad;
            this.grounded = lying;
            push(velocity);
        }

        void push(Pull.Velocity velocity) {
            vx = velocity.x();
            vy = velocity.y();
            vz = velocity.z();
        }

        void step() {
            tick++;
            vy -= Pull.GRAVITY;
            if (grounded && vx * vx + vz * vz <= 1e-5 && tick % 4 != 0) {
                return;
            }
            double next = y + vy;
            if (vy < 0 && y >= pad.surface() && next < pad.surface()) {
                assertTrue(pad.under(x, z), "falls past the pad's top at " + x + ", " + z);
                next = pad.surface();
                vy = 0;
                if (!grounded) {
                    landed = tick;
                }
                grounded = true;
            } else {
                grounded = false;
            }
            y = next;
            apex = Math.max(apex, y);
            if (y < pad.surface() && pad.under(x + vx, z + vz)) {
                assertFalse(strict, "hits the pad's side at height " + y);
                vx = 0;
                vz = 0;
            }
            x += vx;
            z += vz;
            double sideways = grounded ? GRIP : Pull.DRAG;
            vx *= sideways;
            vz *= sideways;
            vy *= Pull.DRAG;
        }

        void run(int ticks) {
            for (int step = 0; step < ticks; step++) {
                step();
            }
        }

        void assertOnCentre(String what) {
            assertTrue(grounded, what);
            assertEquals(pad.surface(), y, 1e-9, what);
            assertEquals(pad.x(), x, ON_CENTRE, what);
            assertEquals(pad.z(), z, ON_CENTRE, what);
        }
    }

    private static Flight throwFrom(double distance, double height, double[] direction) {
        Pad pad = new Pad(distance * direction[0], -height, distance * direction[1]);
        Pull.Arc arc = Pull.toPad(pad.x(), pad.surface(), pad.z(), GRIP);
        assertNotNull(arc, "no throw from " + distance + " at height " + height);
        Flight flight = new Flight(pad, arc.velocity(), false);
        flight.run(SETTLE_TICKS);
        Pull.Offset landing = arc.path().getLast();
        assertEquals(pad.surface(), landing.y());
        return flight;
    }

    @Test
    void aThrowStopsOnThePadsCentre() {
        for (double height : HEIGHTS) {
            for (double distance = 1; distance <= 16; distance += 0.25) {
                for (double[] direction : DIRECTIONS) {
                    // Closer along both axes, the item hops instead.
                    if (Math.max(Math.abs(direction[0]), Math.abs(direction[1])) * distance < 1) {
                        continue;
                    }
                    String from = "from " + distance + " at height " + height;
                    Flight flight = throwFrom(distance, height, direction);
                    flight.assertOnCentre(from);
                    assertTrue(flight.apex >= Math.max(-height, 0) + Pull.LIFT - 1e-9, from + ", rises " + flight.apex);
                }
            }
        }
    }

    // From the foot of a two-block pad.
    @Test
    void aFarItemFliesLonger() {
        int four = throwFrom(4, -2, DIRECTIONS[0]).landed;
        assertTrue(four >= 16 && four <= 22, "about a second, not " + four + " ticks");
        int sixteen = throwFrom(16, -2, DIRECTIONS[0]).landed;
        assertTrue(sixteen > throwFrom(8, -2, DIRECTIONS[0]).landed && sixteen <= Pull.MAX_TICKS);
    }

    // Landing on ice, an item slides on for many blocks: no throw from afar is slow enough to stop on the pad.
    @Test
    void anIcyPadOnlyTakesWhatLandsSoftly() {
        assertNull(Pull.toPad(8, 2, 0, ICE_GRIP));
        assertNotNull(Pull.toPad(1, 2, 0, ICE_GRIP));
    }

    // The drift is worked out when the task runs, from where the item is then, which may be a tick off the top. Rising,
    // an item that touches the pad may rub its side; the drift down onto it may not.
    @Test
    void aHopAndItsDriftStopOnThePadsCentre() {
        double[][] beside = {{0.65, 0}, {0.8, 0.3}, {0.95, 0.95}, {-0.2, 0.9}};
        for (double height : new double[]{-2, 0, 1}) {
            for (double[] at : beside) {
                for (int late = -1; late <= 1; late++) {
                    Pad pad = new Pad(at[0], -height, at[1]);
                    Pull.Hop hop = Pull.hop(pad.x(), pad.surface(), pad.z());
                    Flight flight = new Flight(pad, hop.velocity(), true);
                    flight.strict = false;
                    flight.run(hop.ticks() + late);
                    flight.strict = true;
                    String what = "beside at " + at[0] + ", " + at[1] + ", height " + height + ", " + late + " late";
                    if (late == 0) {
                        assertEquals(Math.max(-height, 0) + Pull.LIFT, flight.y, 0.05, what);
                        assertEquals(hop.height(), flight.y, 1e-9, what);
                    }
                    Pull.Velocity drift = Pull.drift(pad.x() - flight.x, pad.surface() - flight.y,
                            pad.z() - flight.z, flight.vy, GRIP);
                    assertNotNull(drift, what);
                    assertEquals(flight.vy, drift.y(), what);
                    flight.push(drift);
                    flight.run(SETTLE_TICKS);
                    flight.assertOnCentre(what);
                }
            }
        }
    }

    // Without the nudge, the item would wait on the ground for up to three ticks while gravity takes its speed.
    @Test
    void aHopNeverWaitsOnTheGround() {
        Pull.Velocity velocity = Pull.hop(0.7, 2, 0).velocity();
        assertTrue(velocity.x() * velocity.x() + velocity.z() * velocity.z() > 1e-5);
    }

    @Test
    void anItemNoHigherThanThePadDoesntDrift() {
        assertNull(Pull.drift(0.7, 0, 0, 0, GRIP));
        assertNull(Pull.drift(0.7, 0.5, 0, -0.1, GRIP));
    }
}
