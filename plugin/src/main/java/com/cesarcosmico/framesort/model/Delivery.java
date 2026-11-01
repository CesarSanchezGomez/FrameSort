package com.cesarcosmico.framesort.model;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.ToIntBiFunction;

public final class Delivery {

    /** What a sink does with the items that reach it. */
    public enum Route {
        /** Takes what it has room for, when the delivery reaches it. */
        FILL,
        /** Can end its level, and takes all that is left. */
        DROP,
        /** Can end its level when no {@link #DROP} can, and takes all that is left. */
        DESTROY,
        /** Takes no part in this delivery. */
        SKIP
    }

    /** {@code end} is the sink that ended a level and took {@code remaining}, or {@code null} if they stayed. */
    public record Outcome<S>(int remaining, @Nullable S end, boolean endDestroys) {
    }

    private Delivery() {
    }

    /** The sink that ends a level, from the first of each kind in it, or {@code null} when it has neither. */
    public static <S> @Nullable S end(@Nullable S firstDrop, @Nullable S firstDestroy) {
        return firstDrop != null ? firstDrop : firstDestroy;
    }

    /**
     * Goes through each level, best first, and through its sinks in order, routing each one only when it is reached:
     * nothing past the sink that takes the last item is looked at. Once a level's sinks run out with items left,
     * {@link #end} takes them and later levels get nothing. {@code offer} returns how much the sink did <em>not</em>
     * accept (from 0 to the amount offered); {@code commit} receives the new remainder after every sink that took
     * something.
     */
    public static <S> Outcome<S> deliver(int amount, List<List<S>> levels, Function<S, Route> route,
                                         ToIntBiFunction<S, Integer> offer, IntConsumer commit) {
        int remaining = amount;
        for (List<S> level : levels) {
            S drop = null;
            S destroy = null;
            for (S sink : level) {
                switch (route.apply(sink)) {
                    case FILL -> {
                        int left = offer.applyAsInt(sink, remaining);
                        if (left != remaining) {
                            remaining = left;
                            commit.accept(remaining);
                        }
                    }
                    case DROP -> drop = drop == null ? sink : drop;
                    case DESTROY -> destroy = destroy == null ? sink : destroy;
                    case SKIP -> { }
                }
                if (remaining <= 0) {
                    return new Outcome<>(remaining, null, false);
                }
            }
            S end = end(drop, destroy);
            if (end != null) {
                return new Outcome<>(remaining, end, end != drop);
            }
        }
        return new Outcome<>(remaining, null, false);
    }
}
