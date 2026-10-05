package com.cesarcosmico.framesort.model;

/**
 * How a target frame matches an item. A lower priority wins; a match through the contents of a shulker box or
 * bundle held in the frame ranks just below the same kind of direct match.
 */
public enum MatchTier {
    /** Same item, meta included, amount ignored. */
    EXACT(0),
    /** The frame item's name is an item or block tag that contains the item. */
    TAG(10),
    /** Same material. */
    SIMILAR(20);

    /** Frames holding the default target item; used only when nothing else matches. */
    public static final int DEFAULT_PRIORITY = Integer.MAX_VALUE - 1;

    private final int priority;

    MatchTier(int priority) {
        this.priority = priority;
    }

    public int priority(boolean nested) {
        return nested ? priority + 1 : priority;
    }
}
