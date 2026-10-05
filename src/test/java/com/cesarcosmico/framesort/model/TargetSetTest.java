package com.cesarcosmico.framesort.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetSetTest {

    @Test
    void keepsOnlyTheBestPriorityWithTies() {
        TargetSet<String> set = new TargetSet<>();
        set.add(MatchTier.SIMILAR.priority(false), "similar");
        set.add(MatchTier.TAG.priority(false), "tag-1");
        set.add(MatchTier.TAG.priority(true), "nested-tag");
        set.add(MatchTier.TAG.priority(false), "tag-2");

        assertEquals(List.of("tag-1", "tag-2"), set.targets());
    }

    @Test
    void defaultTargetLosesToAnyMatch() {
        TargetSet<String> set = new TargetSet<>();
        set.add(MatchTier.DEFAULT_PRIORITY, "default");
        set.add(MatchTier.SIMILAR.priority(true), "nested-similar");

        assertEquals(List.of("nested-similar"), set.targets());
    }

    @Test
    void relevanceFollowsTheBestSoFar() {
        TargetSet<String> set = new TargetSet<>();
        assertTrue(set.isRelevant(MatchTier.DEFAULT_PRIORITY));
        set.add(MatchTier.EXACT.priority(false), "exact");
        assertTrue(set.isRelevant(0));
        assertFalse(set.isRelevant(1));
    }
}
