package com.cesarcosmico.framesort.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Moves an amount into a series of sinks. The remainder is committed to the source right after every sink, so
 * the source never still holds what a sink already took, whatever happens with the next sink.
 */
public final class Delivery {

    /** Returned by {@link Offer#offer} when the sink no longer exists (a container moved by a piston, broken…). */
    public static final int GONE = -1;

    @FunctionalInterface
    public interface Offer<S> {
        /** Offers {@code amount}; returns how much was <em>not</em> accepted, or {@link #GONE}. */
        int offer(S sink, int amount);
    }

    public record Outcome<S>(int remaining, List<S> gone) {
        public Outcome {
            gone = List.copyOf(gone);
        }
    }

    private Delivery() {
    }

    public static <S> Outcome<S> deliver(int amount, List<S> sinks, Offer<S> offer, IntConsumer commit) {
        int remaining = amount;
        List<S> gone = new ArrayList<>();
        for (S sink : sinks) {
            if (remaining <= 0) {
                break;
            }
            int left = offer.offer(sink, remaining);
            if (left == GONE) {
                gone.add(sink);
                continue;
            }
            left = Math.clamp(left, 0, remaining);
            if (left != remaining) {
                remaining = left;
                commit.accept(remaining);
            }
        }
        return new Outcome<>(remaining, gone);
    }
}
