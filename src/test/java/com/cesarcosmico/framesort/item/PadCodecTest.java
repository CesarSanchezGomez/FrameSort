package com.cesarcosmico.framesort.item;

import com.cesarcosmico.framesort.model.BlockKey;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PadCodecTest {

    private static final UUID WORLD = UUID.randomUUID();

    @Test
    void roundTrips() {
        Map<BlockKey, String> pads = new LinkedHashMap<>();
        pads.put(new BlockKey(WORLD, 10, -59, -3), "simple");
        pads.put(new BlockKey(WORLD, -16, 70, 31), "magma");

        assertEquals(pads, PadCodec.decode(WORLD, PadCodec.encode(pads)));
    }

    @Test
    void skipsMalformedEntries() {
        Map<BlockKey, String> pads = PadCodec.decode(WORLD,
                List.of("1,2,3,simple", "x,2,3,simple", "1,2,simple", "4,5,6,", "7,8,9,magma"));

        assertEquals(Map.of(new BlockKey(WORLD, 1, 2, 3), "simple", new BlockKey(WORLD, 7, 8, 9), "magma"), pads);
    }
}
