package com.cesarcosmico.framesort.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustListTest {

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BETO = UUID.randomUUID();
    private static final UUID CARLA = UUID.randomUUID();

    @Test
    void aSourceSendsToItsOwnersFrames() {
        assertTrue(TrustList.EMPTY.allows(ANA, ANA));
        assertFalse(TrustList.EMPTY.allows(ANA, CARLA));
    }

    @Test
    void trustGoesOneWay() {
        TrustList list = TrustList.EMPTY.with(ANA, BETO, 1);

        assertTrue(list.allows(ANA, BETO));
        assertFalse(list.allows(BETO, ANA));
    }

    @Test
    void trustIsNotTransitive() {
        TrustList list = TrustList.EMPTY.with(ANA, BETO, 1).with(BETO, CARLA, 2);

        assertFalse(list.allows(ANA, CARLA));
    }

    @Test
    void nobodysThingsOnlyMeetEachOther() {
        TrustList list = TrustList.EMPTY.with(ANA, BETO, 1);

        assertTrue(list.allows(null, null));
        assertFalse(list.allows(null, ANA));
        assertFalse(list.allows(ANA, null));
    }

    @Test
    void keepsWhenTrustWasGivenAndWhoGaveIt() {
        TrustList list = TrustList.EMPTY.with(ANA, BETO, 10).with(CARLA, BETO, 20).with(ANA, CARLA, 30);

        assertEquals(Map.of(BETO, 10L, CARLA, 30L), list.trustedBy(ANA));
        assertEquals(List.of(), list.trusting(ANA));
        assertEquals(2, list.trusting(BETO).size());
    }

    @Test
    void givingTrustAgainKeepsTheFirstDate() {
        TrustList list = TrustList.EMPTY.with(ANA, BETO, 10);

        assertSame(list, list.with(ANA, BETO, 99));
        assertEquals(10L, list.trustedBy(ANA).get(BETO));
    }

    @Test
    void revokingLeavesTheRest() {
        TrustList list = TrustList.EMPTY.with(ANA, BETO, 1).with(ANA, CARLA, 2).without(ANA, BETO);

        assertFalse(list.trusts(ANA, BETO));
        assertTrue(list.trusts(ANA, CARLA));
        assertEquals(TrustList.EMPTY, list.without(ANA, CARLA));
    }
}
