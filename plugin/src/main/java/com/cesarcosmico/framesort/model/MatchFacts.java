package com.cesarcosmico.framesort.model;

/**
 * What decides whether a frame item accepts an item: whether the frame item carries a tag and the item is in it,
 * whether the frame item is a shulker box or bundle with contents, and whether the item is the same exact item or of
 * the same kind (material, or custom item).
 */
public record MatchFacts(boolean tagged, boolean inTag, boolean filled, boolean similar, boolean sameKind) {

    /**
     * The priority at which the frame item accepts the item, or {@link MatchTier#NO_MATCH}. {@code nested}: the frame
     * item lies inside a shulker box or bundle in the frame.
     */
    public int priority(boolean nested) {
        if (tagged) {
            return inTag ? MatchTier.TAG.priority(nested) : MatchTier.NO_MATCH;
        }
        if (filled && !nested) {
            return MatchTier.NO_MATCH;
        }
        if (similar) {
            return MatchTier.EXACT.priority(nested);
        }
        return !filled && sameKind ? MatchTier.SIMILAR.priority(nested) : MatchTier.NO_MATCH;
    }
}
