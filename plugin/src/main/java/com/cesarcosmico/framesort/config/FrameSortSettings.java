package com.cesarcosmico.framesort.config;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public record FrameSortSettings(String language, Set<NamespacedKey> disabledWorlds, SorterSettings sorter,
                                TargetSettings targets, DeliverySettings delivery, InspectSettings inspect,
                                boolean ownership) {

    public FrameSortSettings {
        disabledWorlds = Set.copyOf(disabledWorlds);
    }

    public static final Set<String> OPTIONAL_KEYS = Set.of("sorter.custom-name", "sorter.activator.name",
            "sorter.activator.lore", "sorter.activator.glint", "targets.tagged-item.name", "targets.tagged-item.lore",
            "targets.tagged-item.glint");

    /** Sections of vanilla component names, which only Minecraft knows. */
    public static final Set<String> OPEN_KEYS = Set.of("sorter.activator.components");

    /**
     * {@code isItem} accepts what a stack can hold, and {@code problem} says why Minecraft rejects an item in /give
     * syntax; both need a server, so tests pass their own.
     */
    public static FrameSortSettings parse(ConfigReader config, Predicate<Material> isItem,
                                          Function<String, @Nullable String> problem, boolean craftEngine) {
        String language = config.string("language", "en_US");
        if (language.isBlank()) {
            config.warn("language", "empty, using en_US");
            language = "en_US";
        }
        return new FrameSortSettings(
                language,
                worldKeys(config),
                SorterSettings.parse(config.sectionOrEmpty("sorter"), isItem, problem, craftEngine),
                TargetSettings.parse(config.sectionOrEmpty("targets")),
                DeliverySettings.parse(config.sectionOrEmpty("delivery"), isItem, craftEngine),
                InspectSettings.parse(config.sectionOrEmpty("inspect")),
                config.bool("ownership.enabled", true));
    }

    private static Set<NamespacedKey> worldKeys(ConfigReader config) {
        Set<NamespacedKey> keys = new HashSet<>();
        for (String world : config.strings("disabled-worlds")) {
            NamespacedKey key = NamespacedKey.fromString(world.toLowerCase(Locale.ROOT));
            if (key == null) {
                config.warn("disabled-worlds", "'" + world + "' is not a valid world key, ignored");
            } else {
                keys.add(key);
            }
        }
        return keys;
    }

    public boolean worksIn(NamespacedKey world) {
        return !disabledWorlds.contains(world);
    }

    /** Only for a reload: when the server starts, another plugin may not have loaded its worlds yet. */
    public void warnUnknownWorlds(Predicate<NamespacedKey> exists, Consumer<String> warn) {
        for (NamespacedKey world : disabledWorlds) {
            if (!exists.test(world)) {
                warn.accept("config.yml > disabled-worlds: no world has the key '" + world + "'; check it");
            }
        }
    }
}
