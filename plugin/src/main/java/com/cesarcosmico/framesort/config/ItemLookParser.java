package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.item.ItemLook;
import com.cesarcosmico.framesort.item.ItemTemplate;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;

/**
 * Reads an item's look: {@code name}, {@code lore}, {@code glint} and, for an item the plugin creates,
 * {@code components:}, vanilla components by their vanilla name, written as YAML and handed to Minecraft in /give
 * syntax.
 */
public final class ItemLookParser {

    private static final String LEGACY_PROBLEM = "uses § codes, which MiniMessage cannot read; write MiniMessage "
            + "tags. Ignored";

    private ItemLookParser() {
    }

    /** A blank name means none, as a missing one does; so does a name or lore with a § code, which is warned. */
    public static ItemLook look(ConfigReader reader) {
        String name = reader.string("name", "");
        if (ConfigReader.hasLegacyCode(name)) {
            reader.warn("name", LEGACY_PROBLEM);
            name = "";
        }
        List<String> lore = reader.strings("lore");
        if (lore.stream().anyMatch(ConfigReader::hasLegacyCode)) {
            reader.warn("lore", LEGACY_PROBLEM);
            lore = List.of();
        }
        return new ItemLook(name.isBlank() ? null : name, lore, reader.bool("glint", false));
    }

    /**
     * A component Minecraft rejects is reported with its reason and left out. {@code problem} says why an input in
     * /give syntax is rejected, or returns {@code null}: {@link ItemTemplate#problem} on a server.
     */
    public static ItemTemplate template(ConfigReader reader, Material material,
                                        Function<String, @Nullable String> problem) {
        Map<String, String> components = components(reader);
        if (!components.isEmpty() && problem.apply(ItemTemplate.input(material, components)) != null) {
            components = accepted(reader, material, components, problem);
        }
        return new ItemTemplate(material, components, look(reader));
    }

    private static Map<String, String> components(ConfigReader reader) {
        Map<String, String> components = new LinkedHashMap<>();
        if (!reader.isSet("components")) {
            return components;
        }
        ConfigReader section = reader.section("components");
        if (section == null) {
            reader.warn("components", "expected a section of vanilla components, ignored");
            return components;
        }
        for (String name : section.childKeys()) {
            Object value = section.value(name);
            // '' means "without it", as for the other look keys; Minecraft would take it as a model named "minecraft:".
            if (value instanceof String text && text.isBlank()) {
                continue;
            }
            try {
                components.put(name, snbt(value));
            } catch (IllegalArgumentException e) {
                section.warn(name, e.getMessage() + ", ignored");
            }
        }
        return components;
    }

    // Checked one by one only when the whole set fails: some components are valid only together.
    private static Map<String, String> accepted(ConfigReader reader, Material material, Map<String, String> components,
                                                Function<String, @Nullable String> problem) {
        ConfigReader section = reader.sectionOrEmpty("components");
        Map<String, String> accepted = new LinkedHashMap<>();
        components.forEach((name, value) -> {
            String reason = problem.apply(ItemTemplate.input(material, Map.of(name, value)));
            if (reason == null) {
                accepted.put(name, value);
            } else {
                section.warn(name, reason + ", ignored");
            }
        });
        String together = problem.apply(ItemTemplate.input(material, accepted));
        if (together != null) {
            reader.warn("components", together + ", none is used");
            return Map.of();
        }
        return accepted;
    }

    static String snbt(@Nullable Object value) {
        return switch (value) {
            case ConfigurationSection section -> {
                StringJoiner joined = new StringJoiner(",", "{", "}");
                section.getKeys(false).forEach(key -> joined.add(quote(key) + ":" + snbt(section.get(key))));
                yield joined.toString();
            }
            case Map<?, ?> map -> {
                StringJoiner joined = new StringJoiner(",", "{", "}");
                map.forEach((key, child) -> joined.add(quote(String.valueOf(key)) + ":" + snbt(child)));
                yield joined.toString();
            }
            case List<?> list -> {
                StringJoiner joined = new StringJoiner(",", "[", "]");
                list.forEach(child -> joined.add(snbt(child)));
                yield joined.toString();
            }
            case String text -> quote(text);
            case Boolean bool -> bool.toString();
            case Integer number -> number.toString();
            // Without the suffix Minecraft reads a whole number as an int, and one past the int range fails.
            case Long number -> number + "L";
            case Double number when Double.isFinite(number) -> BigDecimal.valueOf(number).toPlainString() + "d";
            case Number number -> throw new IllegalArgumentException("'" + number + "' is not a usable number");
            case null -> throw new IllegalArgumentException("empty value");
            default -> throw new IllegalArgumentException("unsupported value '" + value + "', write it in quotes");
        };
    }

    private static String quote(String text) {
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
