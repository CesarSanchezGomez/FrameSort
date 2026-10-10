package com.cesarcosmico.framesort.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeliveryTest {

    /** A container with room for {@code room} more items, or {@code null} room when it has vanished. */
    private static final class Box {
        private final Integer room;
        private int stored;

        Box(Integer room) {
            this.room = room;
        }

        int offer(int amount) {
            if (room == null) {
                return Delivery.GONE;
            }
            int accepted = Math.min(amount, room - stored);
            stored += accepted;
            return amount - accepted;
        }
    }

    @Test
    void vanishedContainerAfterPartialInsertKeepsTheTotal() {
        // The duplication this guards against: 14 went into A, B was gone and the source still kept all 64.
        Box a = new Box(14);
        Box b = new Box(null);
        int[] source = {64};

        Delivery.Outcome<Box> outcome = Delivery.deliver(64, List.of(a, b), Box::offer, left -> source[0] = left);

        assertEquals(14, a.stored);
        assertEquals(50, source[0]);
        assertEquals(50, outcome.remaining());
        assertEquals(64, a.stored + source[0]);
        assertEquals(List.of(b), outcome.gone());
    }

    @Test
    void vanishedContainerFirstStillUsesTheNextOne() {
        Box gone = new Box(null);
        Box a = new Box(100);
        int[] source = {64};

        Delivery.Outcome<Box> outcome = Delivery.deliver(64, List.of(gone, a), Box::offer, left -> source[0] = left);

        assertEquals(64, a.stored);
        assertEquals(0, source[0]);
        assertEquals(0, outcome.remaining());
    }

    @Test
    void commitsAfterEverySinkThatTookSomething() {
        List<Integer> commits = new ArrayList<>();
        List<Box> boxes = List.of(new Box(10), new Box(0), new Box(20), new Box(100));

        Delivery.deliver(64, boxes, Box::offer, commits::add);

        assertEquals(List.of(54, 34, 0), commits);
    }

    @Test
    void stopsOnceEverythingIsDelivered() {
        Map<Box, Integer> offers = new HashMap<>();
        Box first = new Box(64);
        Box second = new Box(64);

        Delivery.deliver(64, List.of(first, second), (box, amount) -> {
            offers.merge(box, 1, Integer::sum);
            return box.offer(amount);
        }, left -> { });

        assertEquals(1, offers.get(first));
        assertEquals(null, offers.get(second));
    }

    @Test
    void unstackableItemIsAllOrNothing() {
        Box full = new Box(0);
        Box gone = new Box(null);
        int[] source = {1};

        Delivery.Outcome<Box> outcome = Delivery.deliver(1, List.of(full, gone), Box::offer, left -> source[0] = left);

        assertEquals(1, source[0]);
        assertEquals(1, outcome.remaining());
    }
}
