package com.cesarcosmico.framesort.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankingTest {

    @Test
    void groupsTiesBestFirst() {
        Ranking<String> ranking = new Ranking<>();
        ranking.add(MatchTier.SIMILAR.priority(false), "similar");
        ranking.add(MatchTier.TAG.priority(false), "tag-1");
        ranking.add(MatchTier.TAG.priority(true), "nested-tag");
        ranking.add(MatchTier.TAG.priority(false), "tag-2");

        assertEquals(List.of(List.of("tag-1", "tag-2"), List.of("nested-tag"), List.of("similar")), ranking.levels());
    }

    @Test
    void defaultTargetComesLast() {
        Ranking<String> ranking = new Ranking<>();
        ranking.add(MatchTier.DEFAULT_PRIORITY, "default");
        ranking.add(MatchTier.SIMILAR.priority(true), "nested-similar");

        assertEquals(List.of(List.of("nested-similar"), List.of("default")), ranking.levels());
    }
}
