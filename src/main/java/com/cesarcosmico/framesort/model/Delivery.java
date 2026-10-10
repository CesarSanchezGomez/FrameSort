package com.cesarcosmico.framesort.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.ToIntBiFunction;

/** Commits the remainder to the source after every sink, so a failing sink can never duplicate items. */
public final class Delivery {

    /** Returned by the offer when the sink no longer exists (a container moved by a piston, broken…). */
    public static final int GONE = -1;

    public record Outcome<S>(int remaining, List<S> gone) {
        public Outcome {
            gone = List.copyOf(gone);
        }
    }

    private Delivery() {
    }

    /**
     * Offers what is left to each sink in turn. {@code offer} returns how much the sink did <em>not</em> accept
     * (from 0 to the amount offered), or {@link #GONE}; {@code commit} receives the new remainder after every sink
     * that took something.
     */
    public static <S> Outcome<S> deliver(int amount, List<S> sinks, ToIntBiFunction<S, Integer> offer,
                                         IntConsumer commit) {
        int remaining = amount;
        List<S> gone = new ArrayList<>();
        for (S sink : sinks) {
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
        return new Outcome<>(remaining, gone);
    }
}
