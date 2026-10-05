package com.cesarcosmico.framesort.model;

/** A lower priority wins; a match through a shulker box or bundle ranks just below the same direct match. */
public enum MatchTier {
    EXACT(0),
    TAG(10),
    SIMILAR(20);

    public static final int DEFAULT_PRIORITY = Integer.MAX_VALUE - 1;

    private final int priority;

    MatchTier(int priority) {
        this.priority = priority;
    }

    public int priority(boolean nested) {
        return nested ? priority + 1 : priority;
    }
}
