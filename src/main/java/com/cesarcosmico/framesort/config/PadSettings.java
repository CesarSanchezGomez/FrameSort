package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.item.ItemTemplate;
import com.cesarcosmico.framesort.model.PadMode;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

public record PadSettings(PadMode creation, int sweepInterval, Map<String, PadType> types) {

    public PadSettings {
        // Keeps the file's order, so lists and suggestions follow it.
        types = Collections.unmodifiableMap(new LinkedHashMap<>(types));
    }

    /**
     * {@code isBlock} and {@code isItem} need the registries on a server, so tests pass their own; {@code isItem}
     * leaves out air.
     */
    public static PadSettings parse(ConfigReader reader, Predicate<Material> isBlock, Predicate<Material> isItem) {
        Map<String, PadType> types = new LinkedHashMap<>();
        ConfigReader section = reader.section("types");
        if (section != null) {
            for (String id : section.childKeys()) {
                PadType type = parseType(section, id, isBlock, isItem);
                if (type != null) {
                    types.put(type.id(), type);
                }
            }
        }
        if (types.isEmpty()) {
            reader.warn("types", "no valid pad types; teleport pads are disabled");
        }
        return new PadSettings(
                reader.enumValue("creation", PadMode.class, PadMode.ANYONE),
                reader.integer("sweep-interval", 10, 1, 200),
                types);
    }

    public @Nullable PadType type(String id) {
        return types.get(id.toLowerCase(Locale.ROOT));
    }

    private static @Nullable PadType parseType(ConfigReader types, String id, Predicate<Material> isBlock,
                                               Predicate<Material> isItem) {
        ConfigReader reader = types.section(id);
        if (reader == null) {
            types.warn(id, "expected a section with a 'structure' list");
            return null;
        }
        List<Material> structure = reader.materials("structure");
        if (structure.isEmpty()) {
            reader.warn("structure", "no valid blocks; pad type '" + id + "' is skipped");
            return null;
        }
        if (!structure.stream().allMatch(isBlock)) {
            reader.warn("structure", "every entry must be a block; pad type '" + id + "' is skipped");
            return null;
        }
        // The top block is placed by hand and handed out by /framesort give pad.
        if (!isItem.test(structure.getFirst())) {
            reader.warn("structure", structure.getFirst() + " is not an item, so it can't be placed; pad type '"
                    + id + "' is skipped");
            return null;
        }
        ItemTemplate item = SorterSettings.itemTemplate(reader.sectionOrEmpty("item"), structure.getFirst(), null);
        return new PadType(id.toLowerCase(Locale.ROOT), structure, item);
    }
}
