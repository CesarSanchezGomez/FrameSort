package com.cesarcosmico.framesort.config;

import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public record FrameSortSettings(String language, SorterSettings sorter, TargetSettings targets,
                                DeliverySettings delivery, InspectSettings inspect) {

    /** Keys whose absence means "without it": the activator's and a tagged item's look, and the sorter's title. */
    public static final Set<String> OPTIONAL_KEYS = Set.of("sorter.custom-name", "sorter.activator.name",
            "sorter.activator.lore", "sorter.activator.glint", "targets.tagged-item.name", "targets.tagged-item.lore",
            "targets.tagged-item.glint");

    /** Sections of vanilla component names, which only Minecraft knows. */
    public static final Set<String> OPEN_KEYS = Set.of("sorter.activator.components");

    /**
     * {@code isItem} accepts what a stack can hold, and {@code problem} says why Minecraft rejects an item in /give
     * syntax; both need a server, so tests pass their own. {@code craftEngine} says whether CraftEngine is enabled.
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
                SorterSettings.parse(config.sectionOrEmpty("sorter"), isItem, problem, craftEngine),
                TargetSettings.parse(config.sectionOrEmpty("targets")),
                DeliverySettings.parse(config.sectionOrEmpty("delivery"), isItem, craftEngine),
                InspectSettings.parse(config.sectionOrEmpty("inspect")));
    }
}
