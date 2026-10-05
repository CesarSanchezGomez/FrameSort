package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.item.ItemTemplate;
import com.cesarcosmico.framesort.model.PadMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** {@code pads.yml}: who can create teleport pads and which block columns count as one. */
public record PadSettings(PadMode creation, int sweepInterval, Map<String, PadType> types) {

    public PadSettings {
        // Keeps the file's order, so lists and suggestions follow it.
        types = Collections.unmodifiableMap(new LinkedHashMap<>(types));
    }

    /** {@code isBlock} is {@code Material::isBlock} on a server; it needs the registries, so tests pass their own. */
    public static PadSettings parse(ConfigurationSection root, Consumer<String> warn, Predicate<Material> isBlock) {
        ConfigReader reader = new ConfigReader(root, "pads.yml", warn);
        Map<String, PadType> types = new LinkedHashMap<>();
        ConfigReader section = reader.section("types");
        if (section != null) {
            for (String id : section.childKeys()) {
                PadType type = parseType(section, id, isBlock);
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

    private static @Nullable PadType parseType(ConfigReader types, String id, Predicate<Material> isBlock) {
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
        ItemTemplate item = ItemTemplate.parse(reader.sectionOrEmpty("item"), structure.getFirst());
        return new PadType(id.toLowerCase(Locale.ROOT), structure, item);
    }
}
