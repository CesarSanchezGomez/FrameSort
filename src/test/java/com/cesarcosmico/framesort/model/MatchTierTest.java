package com.cesarcosmico.framesort.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchTierTest {

    private static final int NO = MatchTier.NO_MATCH;

    @Test
    void theBestOfTheFrameItemAndItsContentsWins() {
        assertEquals(MatchTier.TAG.priority(false),
                MatchTier.best(MatchTier.TAG.priority(false), List.of(MatchTier.SIMILAR.priority(true)), false));
        assertEquals(MatchTier.TAG.priority(true),
                MatchTier.best(MatchTier.SIMILAR.priority(false), List.of(MatchTier.TAG.priority(true)), false));
        assertEquals(MatchTier.EXACT.priority(true),
                MatchTier.best(NO, List.of(NO, MatchTier.SIMILAR.priority(true), MatchTier.EXACT.priority(true)),
                        false));
    }

    @Test
    void theDefaultTargetOnlyTakesWhatNothingElseMatches() {
        assertEquals(MatchTier.DEFAULT_PRIORITY, MatchTier.best(NO, List.of(NO), true));
        assertEquals(NO, MatchTier.best(NO, List.of(NO), false));
        assertEquals(MatchTier.SIMILAR.priority(true), MatchTier.best(NO, List.of(MatchTier.SIMILAR.priority(true)),
                true));
    }
}
