package com.cesarcosmico.framesort.model;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.ToIntBiFunction;

/** Commits the remainder to the source after every sink, so a failing sink can never duplicate items. */
public final class Delivery {

    /** Returned by the offer when the sink no longer exists (a container moved by a piston, broken…). */
    public static final int GONE = -1;

    /** One level of preference: its sinks fill first, then {@code end}, if any, takes whatever is left. */
    public record Level<S, E>(List<S> sinks, @Nullable E end) {
        public Level {
            sinks = List.copyOf(sinks);
        }
    }

    /** {@code end} is the level end that took the {@code remaining} items, or {@code null} if they stayed. */
    public record Outcome<S, E>(int remaining, List<S> gone, @Nullable E end) {
        public Outcome {
            gone = List.copyOf(gone);
        }
    }

    private Delivery() {
    }

    /**
     * Offers what is left to each level in turn, best first. {@code offer} returns how much the sink did
     * <em>not</em> accept (from 0 to the amount offered), or {@link #GONE}; {@code commit} receives the new remainder
     * after every sink that took something.
     */
    public static <S, E> Outcome<S, E> deliver(int amount, List<Level<S, E>> levels,
                                               ToIntBiFunction<S, Integer> offer, IntConsumer commit) {
        int remaining = amount;
        List<S> gone = new ArrayList<>();
        for (Level<S, E> level : levels) {
            for (S sink : level.sinks()) {
                if (remaining <= 0) {
                    break;
                }
                int left = offer.applyAsInt(sink, remaining);
                if (left == GONE) {
                    gone.add(sink);
                    continue;
                }
                if (left != remaining) {
                    remaining = left;
                    commit.accept(remaining);
                }
            }
            if (remaining <= 0) {
                break;
            }
            if (level.end() != null) {
                return new Outcome<>(remaining, gone, level.end());
            }
        }
        return new Outcome<>(remaining, gone, null);
    }
}
