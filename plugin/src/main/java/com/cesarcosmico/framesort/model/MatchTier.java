package com.cesarcosmico.framesort.model;

import java.util.List;

/** A lower priority wins. */
public enum MatchTier {
    EXACT(0),
    TAG(10),
    SIMILAR(20);

    public static final int NO_MATCH = -1;
    public static final int DEFAULT_PRIORITY = Integer.MAX_VALUE - 1;

    private final int priority;

    MatchTier(int priority) {
        this.priority = priority;
    }

    public int priority(boolean nested) {
        return nested ? priority + 1 : priority;
    }

    /**
     * A frame's priority: the best of its item's own ({@code direct}) and those of the items inside it
     * ({@code nested}), or {@link #DEFAULT_PRIORITY} for the default target when none of them matches.
     */
    public static int best(int direct, List<Integer> nested, boolean defaultTarget) {
        int best = direct;
        for (int priority : nested) {
            if (priority != NO_MATCH && (best == NO_MATCH || priority < best)) {
                best = priority;
            }
        }
        return best == NO_MATCH && defaultTarget ? DEFAULT_PRIORITY : best;
    }
}
