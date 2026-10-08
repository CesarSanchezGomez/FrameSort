package com.cesarcosmico.framesort.item;

import com.cesarcosmico.framesort.model.BlockKey;
import org.bukkit.Chunk;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PadCodec {

    // Nine digits always fit an int and cover every block coordinate (at most 30 million).
    private static final Pattern ENTRY = Pattern.compile("(-?\\d{1,9}),(-?\\d{1,9}),(-?\\d{1,9}),(.+)");

    private PadCodec() {
    }

    public static Map<BlockKey, String> read(Chunk chunk) {
        List<String> entries = chunk.getPersistentDataContainer().get(Keys.PADS, PersistentDataType.LIST.strings());
        return entries == null ? Map.of() : decode(chunk.getWorld().getUID(), entries);
    }

    public static void write(Chunk chunk, Map<BlockKey, String> pads) {
        if (pads.isEmpty()) {
            chunk.getPersistentDataContainer().remove(Keys.PADS);
        } else {
            chunk.getPersistentDataContainer().set(Keys.PADS, PersistentDataType.LIST.strings(), encode(pads));
        }
    }

    static List<String> encode(Map<BlockKey, String> pads) {
        return pads.entrySet().stream()
                .map(entry -> {
                    BlockKey key = entry.getKey();
                    return key.x() + "," + key.y() + "," + key.z() + "," + entry.getValue();
                })
                .toList();
    }

    /** Malformed entries are dropped: a hand-edited chunk must not stop the others from loading. */
    static Map<BlockKey, String> decode(UUID world, List<String> entries) {
        Map<BlockKey, String> pads = new LinkedHashMap<>();
        for (String entry : entries) {
            Matcher parts = ENTRY.matcher(entry);
            if (parts.matches() && !parts.group(4).isBlank()) {
                pads.put(new BlockKey(world, Integer.parseInt(parts.group(1)), Integer.parseInt(parts.group(2)),
                        Integer.parseInt(parts.group(3))), parts.group(4));
            }
        }
        return Collections.unmodifiableMap(pads);
    }
}
