package com.cesarcosmico.framesort.item;

import com.cesarcosmico.framesort.model.BlockKey;
import com.cesarcosmico.framesort.model.PadEntry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PadCodecTest {

    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();

    @Test
    void roundTrips() {
        Map<BlockKey, PadEntry> pads = new LinkedHashMap<>();
        pads.put(new BlockKey(WORLD, 10, -59, -3), new PadEntry("simple", OWNER));
        pads.put(new BlockKey(WORLD, -16, 70, 31), new PadEntry("magma", null));

        assertEquals(pads, PadCodec.decode(WORLD, PadCodec.encode(pads)));
    }

    @Test
    void aPadSavedBeforeOwnersHasNone() {
        assertEquals(Map.of(new BlockKey(WORLD, 1, 2, 3), new PadEntry("simple", null)),
                PadCodec.decode(WORLD, List.of("1,2,3,simple")));
    }

    @Test
    void anUnreadableOwnerCountsAsNobody() {
        assertEquals(Map.of(new BlockKey(WORLD, 1, 2, 3), new PadEntry("simple", null)),
                PadCodec.decode(WORLD, List.of("1,2,3,simple,not-a-uuid")));
    }

    @Test
    void skipsMalformedEntries() {
        Map<BlockKey, PadEntry> pads = PadCodec.decode(WORLD, List.of("1,2,3,simple", "x,2,3,simple", "1,2,simple",
                "4,5,6,", "7,8,9,magma," + OWNER, "1,1,1,simple," + OWNER + ",extra"));

        assertEquals(Map.of(new BlockKey(WORLD, 1, 2, 3), new PadEntry("simple", null),
                new BlockKey(WORLD, 7, 8, 9), new PadEntry("magma", OWNER)), pads);
    }
}
