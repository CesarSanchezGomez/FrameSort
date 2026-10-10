package com.cesarcosmico.framesort.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    private static List<Delivery.Level<Box, Box>> oneLevel(Box... boxes) {
        return List.of(new Delivery.Level<>(List.of(boxes), null));
    }

    @Test
    void vanishedContainerAfterPartialInsertKeepsTheTotal() {
        // The duplication this guards against: 14 went into A, B was gone and the source still kept all 64.
        Box a = new Box(14);
        Box b = new Box(null);
        int[] source = {64};

        Delivery.Outcome<Box, Box> outcome = Delivery.deliver(64, oneLevel(a, b), Box::offer, left -> source[0] = left);

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

        Delivery.Outcome<Box, Box> outcome = Delivery.deliver(64, oneLevel(gone, a), Box::offer,
                left -> source[0] = left);

        assertEquals(64, a.stored);
        assertEquals(0, source[0]);
        assertEquals(0, outcome.remaining());
    }

    @Test
    void commitsAfterEverySinkThatTookSomething() {
        List<Integer> commits = new ArrayList<>();

        Delivery.deliver(64, oneLevel(new Box(10), new Box(0), new Box(20), new Box(100)), Box::offer, commits::add);

        assertEquals(List.of(54, 34, 0), commits);
    }

    @Test
    void stopsOnceEverythingIsDelivered() {
        Map<Box, Integer> offers = new HashMap<>();
        Box first = new Box(64);
        Box second = new Box(64);

        Delivery.deliver(64, oneLevel(first, second), (box, amount) -> {
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

        Delivery.Outcome<Box, Box> outcome = Delivery.deliver(1, oneLevel(full, gone), Box::offer,
                left -> source[0] = left);

        assertEquals(1, source[0]);
        assertEquals(1, outcome.remaining());
    }

    @Test
    void fullLevelPassesTheRestToTheNext() {
        Box preferred = new Box(10);
        Box fallback = new Box(100);
        int[] source = {64};
        List<Delivery.Level<Box, Box>> levels = List.of(
                new Delivery.Level<>(List.of(preferred), null),
                new Delivery.Level<>(List.of(fallback), null));

        Delivery.Outcome<Box, Box> outcome = Delivery.deliver(64, levels, Box::offer, left -> source[0] = left);

        assertEquals(10, preferred.stored);
        assertEquals(54, fallback.stored);
        assertEquals(0, source[0]);
        assertEquals(0, outcome.remaining());
    }

    @Test
    void endTakesWhatIsLeftAndLaterLevelsGetNothing() {
        Box preferred = new Box(10);
        Box end = new Box(0);
        Box later = new Box(100);
        List<Delivery.Level<Box, Box>> levels = List.of(
                new Delivery.Level<>(List.of(preferred), end),
                new Delivery.Level<>(List.of(later), null));

        Delivery.Outcome<Box, Box> outcome = Delivery.deliver(64, levels, Box::offer, left -> { });

        assertEquals(end, outcome.end());
        assertEquals(54, outcome.remaining());
        assertEquals(0, later.stored);
    }

    @Test
    void endIsUnusedWhenTheSinksTookEverything() {
        Box preferred = new Box(100);
        Box end = new Box(0);

        Delivery.Outcome<Box, Box> outcome = Delivery.deliver(64,
                List.of(new Delivery.Level<>(List.of(preferred), end)), Box::offer, left -> { });

        assertNull(outcome.end());
        assertEquals(0, outcome.remaining());
    }

    @Test
    void withoutRoomAnywhereEverythingStays() {
        int[] source = {64};
        List<Delivery.Level<Box, Box>> levels = List.of(
                new Delivery.Level<>(List.of(new Box(0)), null),
                new Delivery.Level<>(List.of(new Box(0)), null));

        Delivery.Outcome<Box, Box> outcome = Delivery.deliver(64, levels, Box::offer, left -> source[0] = left);

        assertNull(outcome.end());
        assertEquals(64, outcome.remaining());
        assertEquals(64, source[0]);
    }
}
