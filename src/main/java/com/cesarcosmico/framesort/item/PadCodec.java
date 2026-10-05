package com.cesarcosmico.framesort.item;

import com.cesarcosmico.framesort.model.BlockKey;
import org.bukkit.Chunk;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PadCodec {

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
        List<String> entries = new ArrayList<>();
        pads.forEach((key, type) -> entries.add(key.x() + "," + key.y() + "," + key.z() + "," + type));
        return entries;
    }

    /** Malformed entries are dropped: a hand-edited chunk must not stop the others from loading. */
    static Map<BlockKey, String> decode(UUID world, List<String> entries) {
        Map<BlockKey, String> pads = new LinkedHashMap<>();
        for (String entry : entries) {
            String[] parts = entry.split(",", 4);
            if (parts.length != 4 || parts[3].isBlank()) {
                continue;
            }
            try {
                pads.put(new BlockKey(world, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2])), parts[3]);
            } catch (NumberFormatException ignored) {
                // Skipped on purpose, see above.
            }
        }
        return pads;
    }
}
