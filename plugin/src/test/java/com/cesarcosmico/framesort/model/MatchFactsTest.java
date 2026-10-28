package com.cesarcosmico.framesort.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchFactsTest {

    private static final int NO = MatchTier.NO_MATCH;

    private static MatchFacts facts(boolean tagged, boolean inTag, boolean filled, boolean similar, boolean sameKind) {
        return new MatchFacts(tagged, inTag, filled, similar, sameKind);
    }

    @Test
    void aTaggedFrameItemStandsOnlyForItsTag() {
        assertEquals(MatchTier.TAG.priority(false), facts(true, true, false, false, false).priority(false));
        assertEquals(MatchTier.TAG.priority(true), facts(true, true, false, false, false).priority(true));
        assertEquals(NO, facts(true, false, false, true, true).priority(false));
        assertEquals(MatchTier.TAG.priority(false), facts(true, true, true, false, false).priority(false));
    }

    @Test
    void theExactItemBeatsTheSameKind() {
        assertEquals(MatchTier.EXACT.priority(false), facts(false, false, false, true, true).priority(false));
        assertEquals(MatchTier.SIMILAR.priority(false), facts(false, false, false, false, true).priority(false));
        assertEquals(NO, facts(false, false, false, false, false).priority(false));
    }

    @Test
    void aFilledShulkerBoxOrBundleInTheFrameStandsOnlyForItsContents() {
        assertEquals(NO, facts(false, false, true, true, true).priority(false));
    }

    @Test
    void insideTheFrameItemAFilledOneTakesOnlyTheExactItem() {
        assertEquals(MatchTier.EXACT.priority(true), facts(false, false, true, true, true).priority(true));
        assertEquals(NO, facts(false, false, true, false, true).priority(true));
        assertEquals(MatchTier.SIMILAR.priority(true), facts(false, false, false, false, true).priority(true));
    }
}
