package com.cesarcosmico.framesort.model;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * How a pad throws a loose item onto itself, under the game's item physics. In the air, each tick the vertical speed
 * drops by {@link #GRAVITY}, the item moves, up or down first and then sideways, and its speed is multiplied by
 * {@link #DRAG}. Landing on a block stops its fall without a bounce, and on the ground its sideways speed is multiplied
 * after every move by the grip: the block's friction times {@link #DRAG}, below 1. Offsets go from the item to the
 * centre of the pad's surface, in blocks; speeds are in blocks per tick.
 */
public final class Pull {

    public record Velocity(double x, double y, double z) {
    }

    public record Offset(double x, double y, double z) {
    }

    /**
     * A throw onto the pad: its starting velocity, and the points after the item's own that its flight goes through,
     * in order: the top of the flight, raised by the item's height, then the corner where it comes over the pad's edge,
     * and where it lands.
     */
    public record Arc(Velocity velocity, List<Offset> path) {
        public Arc {
            path = List.copyOf(path);
        }
    }

    /** A hop straight up beside the pad: its starting velocity, how high it rises and the tick it stops rising. */
    public record Hop(Velocity velocity, double height, int ticks) {
    }

    private record Peak(double height, int ticks) {
    }

    // ItemEntity#getDefaultGravity and Entity#getAirDrag; they change with Minecraft, not with Paper.
    static final double GRAVITY = 0.04;
    static final double DRAG = 0.98;
    // How far every pull rises over the pad's surface and over where the item lay, so the arc shows.
    static final double LIFT = 1;
    // Half a pad plus half an item: sideways from the pad's centre, an item further than this along both axes can't
    // touch the pad.
    static final double EDGE = 0.625;
    // An arc comes over the pad's edge at least this high over its surface, so it never clips the corner.
    static final double EDGE_CLEARANCE = 0.25;
    // An item landing this far from the centre has landed on the pad, whose top is a block wide.
    static final double MAX_SLIDE = 0.4;
    // Aiming a little under the surface makes the item touch it on the throw's last tick, not on the tick after.
    static final double SINK = 0.05;
    // An item's box is a quarter block tall: at the top of the flight, that is what meets a roof.
    static final double ITEM_HEIGHT = 0.25;
    // A throw takes half a second plus 1.5 ticks per block, up to 22 ticks, so a far item doesn't fly like a dart.
    static final int MIN_TICKS = 10;
    static final double TICKS_PER_BLOCK = 1.5;
    static final int PACED_TICKS = 22;
    // Two seconds: an item that no throw brings in by then stays where it is.
    static final int MAX_TICKS = 40;
    // A lying item with less sideways speed than about 0.003 moves on one tick in four; this keeps it moving.
    static final double NUDGE = 0.01;
    // More upward speed than any hop within a pad's radius needs.
    private static final double MAX_UP = 4;
    private static final int SEARCH_STEPS = 50;

    private Pull() {
    }

    /** The grip of a block an item lies on, from its friction (its slipperiness, 0.6 for most blocks). */
    public static double grip(double friction) {
        return friction * DRAG;
    }

    /**
     * The throw from beside the pad that lands short of its centre by as much as the item then slides, so it stops
     * there. It rises {@link #LIFT} over the pad and over the item, comes over the pad's edge {@link #EDGE_CLEARANCE}
     * over its surface and lands on the pad; the further the item, the longer it flies. The item lies at least a block
     * from the centre along one axis. {@code null} when no throw up to {@link #MAX_TICKS} does all that.
     */
    public static @Nullable Arc toPad(double dx, double dy, double dz, double grip) {
        double distance = Math.hypot(dx, dz);
        int paced = (int) Math.min(PACED_TICKS, Math.round(MIN_TICKS + TICKS_PER_BLOCK * distance));
        for (int ticks = paced; ticks <= MAX_TICKS; ticks++) {
            double slides = slides(ticks, grip);
            if (distance * slides / (1 + slides) > MAX_SLIDE) {
                continue;
            }
            double scale = 1 / ((1 + slides) * reach(ticks));
            Velocity velocity = new Velocity(dx * scale, (dy - SINK + fall(ticks)) / reach(ticks), dz * scale);
            Arc arc = arc(velocity, ticks, dx, dy, dz, 1 / (1 + slides));
            if (arc != null) {
                return arc;
            }
        }
        return null;
    }

    /** The hop of an item beside the pad, straight up to {@link #LIFT} over the pad and over the item. */
    public static Hop hop(double dx, double dy, double dz) {
        double rise = Math.max(dy, 0) + LIFT;
        double low = 0;
        double high = MAX_UP;
        for (int step = 0; step < SEARCH_STEPS; step++) {
            double up = (low + high) / 2;
            if (peak(up).height() < rise) {
                low = up;
            } else {
                high = up;
            }
        }
        double distance = Math.hypot(dx, dz);
        Peak peak = peak(high);
        return new Hop(new Velocity(dx / distance * NUDGE, high, dz / distance * NUDGE), peak.height(), peak.ticks());
    }

    /**
     * The velocity that brings an item, falling at {@code vy}, down onto the pad's centre: it keeps its vertical speed
     * and lands short of the centre by as much as it then slides. {@code null} once it is no higher than the surface.
     */
    public static @Nullable Velocity drift(double dx, double dy, double dz, double vy, double grip) {
        if (dy >= 0) {
            return null;
        }
        int ticks = 0;
        double height = 0;
        double speed = vy;
        while (height > dy) {
            speed -= GRAVITY;
            height += speed;
            speed *= DRAG;
            ticks++;
        }
        double scale = 1 / ((1 + slides(ticks, grip)) * reach(ticks));
        return new Velocity(dx * scale, vy, dz * scale);
    }

    // The item moves up or down before sideways, so it lands where the tick before left it, which must already be over
    // the pad; and it comes over the pad's edge at the height the tick that brings it there has just reached.
    private static @Nullable Arc arc(Velocity velocity, int ticks, double dx, double dy, double dz, double lands) {
        double distance = Math.hypot(dx, dz);
        // Along the straight line to the centre, the item is over the pad once its larger axis is within EDGE.
        double overPad = distance - EDGE * distance / Math.max(Math.abs(dx), Math.abs(dz));
        double[] runs = new double[ticks + 1];
        double[] heights = new double[ticks + 1];
        double speed = Math.hypot(velocity.x(), velocity.z());
        double vy = velocity.y();
        int apexTick = 0;
        int edgeTick = ticks;
        for (int tick = 1; tick <= ticks; tick++) {
            vy -= GRAVITY;
            runs[tick] = runs[tick - 1] + speed;
            heights[tick] = heights[tick - 1] + vy;
            if (heights[tick] > heights[apexTick]) {
                apexTick = tick;
            }
            if (edgeTick == ticks && runs[tick] > overPad) {
                edgeTick = tick;
            }
            speed *= DRAG;
            vy *= DRAG;
        }
        if (edgeTick == ticks || heights[apexTick] < Math.max(dy, 0) + LIFT
                || heights[edgeTick] < dy + EDGE_CLEARANCE) {
            return null;
        }
        double alongX = dx / distance;
        double alongZ = dz / distance;
        List<Offset> path = new ArrayList<>();
        if (apexTick < edgeTick - 1) {
            path.add(new Offset(alongX * runs[apexTick], heights[apexTick] + ITEM_HEIGHT, alongZ * runs[apexTick]));
        }
        int before = edgeTick - 1;
        path.add(new Offset(alongX * runs[before], heights[before], alongZ * runs[before]));
        path.add(new Offset(alongX * runs[before], heights[edgeTick], alongZ * runs[before]));
        path.add(new Offset(alongX * runs[edgeTick], heights[edgeTick], alongZ * runs[edgeTick]));
        if (apexTick > edgeTick) {
            path.add(new Offset(alongX * runs[apexTick], heights[apexTick] + ITEM_HEIGHT, alongZ * runs[apexTick]));
        }
        path.add(new Offset(dx * lands, dy, dz * lands));
        return new Arc(velocity, path);
    }

    // How far an item slides once it lands, per block it still has to go: it lands at the speed of its last move, and
    // the ground then takes away all but the grip of it on every tick.
    private static double slides(int ticks, double grip) {
        return grip / (1 - grip) * Math.pow(DRAG, ticks - 1) / reach(ticks);
    }

    // The blocks one block per tick of starting speed carries an item, sideways, over a flight of that many ticks.
    private static double reach(int ticks) {
        return (1 - Math.pow(DRAG, ticks)) / (1 - DRAG);
    }

    // How far gravity pulls an item down over a flight of that many ticks.
    private static double fall(int ticks) {
        return GRAVITY / (1 - DRAG) * (ticks - DRAG * reach(ticks));
    }

    private static Peak peak(double up) {
        double height = 0;
        int ticks = 0;
        double speed = up - GRAVITY;
        while (speed > 0) {
            height += speed;
            ticks++;
            speed = speed * DRAG - GRAVITY;
        }
        return new Peak(height, ticks);
    }
}
