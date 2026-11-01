package com.cesarcosmico.framesort.model;

import com.cesarcosmico.framesort.model.Delivery.Route;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryTest {

    // A container with room for that many more items.
    private static final class Box {
        private final int room;
        private final Route route;
        private int stored;

        Box(int room) {
            this(room, Route.FILL);
        }

        Box(int room, Route route) {
            this.room = room;
            this.route = route;
        }

        int offer(int amount) {
            int accepted = Math.min(amount, room - stored);
            stored += accepted;
            return amount - accepted;
        }
    }

    private static Box end(Route route) {
        return new Box(0, route);
    }

    private static Delivery.Outcome<Box> deliver(int amount, List<List<Box>> levels, int[] source) {
        return Delivery.deliver(amount, levels, box -> box.route, Box::offer, left -> source[0] = left);
    }

    @Test
    void partOfTheItemsStayInTheSourceWhenTheContainersFill() {
        Box a = new Box(14);
        Box b = new Box(0);
        int[] source = {64};

        Delivery.Outcome<Box> outcome = deliver(64, List.of(List.of(a, b)), source);

        assertEquals(14, a.stored);
        assertEquals(50, source[0]);
        assertEquals(50, outcome.remaining());
    }

    @Test
    void commitsAfterEverySinkThatTookSomething() {
        List<Integer> commits = new ArrayList<>();
        List<List<Box>> levels = List.of(List.of(new Box(10), new Box(0), new Box(20), new Box(100)));

        Delivery.deliver(64, levels, box -> box.route, Box::offer, commits::add);

        assertEquals(List.of(54, 34, 0), commits);
    }

    @Test
    void stopsOnceEverythingIsDelivered() {
        Map<Box, Integer> offers = new HashMap<>();
        Box first = new Box(64);
        Box second = new Box(64);

        Delivery.deliver(64, List.of(List.of(first, second)), box -> box.route, (box, amount) -> {
            offers.merge(box, 1, Integer::sum);
            return box.offer(amount);
        }, left -> { });

        assertEquals(1, offers.get(first));
        assertEquals(null, offers.get(second));
    }

    @Test
    void routesNothingPastTheSinkThatTookTheLastItem() {
        List<Box> routed = new ArrayList<>();
        Box first = new Box(64);
        Box second = new Box(64);
        Box later = new Box(64);

        Delivery.deliver(64, List.of(List.of(first, second), List.of(later)), box -> {
            routed.add(box);
            return box.route;
        }, Box::offer, left -> { });

        assertEquals(List.of(first), routed);
    }

    @Test
    void unstackableItemIsAllOrNothing() {
        int[] source = {1};

        Delivery.Outcome<Box> outcome = deliver(1, List.of(List.of(new Box(0))), source);

        assertEquals(1, source[0]);
        assertEquals(1, outcome.remaining());
    }

    @Test
    void fullLevelPassesTheRestToTheNext() {
        Box preferred = new Box(10);
        Box fallback = new Box(100);
        int[] source = {64};

        Delivery.Outcome<Box> outcome = deliver(64, List.of(List.of(preferred), List.of(fallback)), source);

        assertEquals(10, preferred.stored);
        assertEquals(54, fallback.stored);
        assertEquals(0, source[0]);
        assertEquals(0, outcome.remaining());
    }

    @Test
    void endTakesWhatIsLeftAndLaterLevelsGetNothing() {
        Box preferred = new Box(10);
        Box end = end(Route.DROP);
        Box later = new Box(100);

        Delivery.Outcome<Box> outcome = deliver(64, List.of(List.of(preferred, end), List.of(later)), new int[]{64});

        assertEquals(end, outcome.end());
        assertEquals(54, outcome.remaining());
        assertEquals(0, later.stored);
    }

    @Test
    void endIsUnusedWhenTheSinksTookEverything() {
        Box preferred = new Box(100);

        Delivery.Outcome<Box> outcome = deliver(64, List.of(List.of(preferred, end(Route.DROP))), new int[]{64});

        assertNull(outcome.end());
        assertEquals(0, outcome.remaining());
    }

    @Test
    void aContainerFartherThanTheEndStillFillsFirst() {
        Box end = end(Route.DROP);
        Box fartherContainer = new Box(100);

        Delivery.Outcome<Box> outcome = deliver(64, List.of(List.of(end, fartherContainer)), new int[]{64});

        assertEquals(64, fartherContainer.stored);
        assertNull(outcome.end());
    }

    @Test
    void theNearestDropEndsTheLevelEvenWithLavaNearer() {
        Box lava = end(Route.DESTROY);
        Box nearestDrop = end(Route.DROP);
        Box fartherDrop = end(Route.DROP);

        Delivery.Outcome<Box> outcome = deliver(64, List.of(List.of(lava, nearestDrop, fartherDrop)), new int[]{64});

        assertEquals(nearestDrop, outcome.end());
        assertFalse(outcome.endDestroys());
    }

    @Test
    void theNearestLavaEndsTheLevelWithoutADrop() {
        Box nearestLava = end(Route.DESTROY);
        Box fartherLava = end(Route.DESTROY);

        Delivery.Outcome<Box> outcome = deliver(64, List.of(List.of(nearestLava, fartherLava)), new int[]{64});

        assertEquals(nearestLava, outcome.end());
        assertTrue(outcome.endDestroys());
    }

    @Test
    void aSkippedSinkIsNeitherOfferedNorAnEnd() {
        Box skipped = new Box(100, Route.SKIP);
        Box fallback = new Box(100);

        Delivery.Outcome<Box> outcome = deliver(64, List.of(List.of(skipped), List.of(fallback)), new int[]{64});

        assertEquals(0, skipped.stored);
        assertEquals(64, fallback.stored);
        assertNull(outcome.end());
    }

    @Test
    void withoutRoomAnywhereEverythingStays() {
        int[] source = {64};

        Delivery.Outcome<Box> outcome = deliver(64, List.of(List.of(new Box(0)), List.of(new Box(0))), source);

        assertNull(outcome.end());
        assertEquals(64, outcome.remaining());
        assertEquals(64, source[0]);
    }
}
