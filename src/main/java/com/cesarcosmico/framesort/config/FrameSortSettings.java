package com.cesarcosmico.framesort.config;

import org.bukkit.Material;

import java.util.function.Predicate;

public record FrameSortSettings(String language, SorterSettings sorter, TargetSettings targets,
                                DeliverySettings delivery, InspectSettings inspect) {

    /** {@code isItem} accepts what a stack can hold; it needs the registries on a server, so tests pass their own. */
    public static FrameSortSettings parse(ConfigReader config, Predicate<Material> isItem) {
        String language = config.string("language", "en_US");
        if (language.isBlank()) {
            config.warn("language", "empty, using en_US");
            language = "en_US";
        }
        return new FrameSortSettings(
                language,
                SorterSettings.parse(config.sectionOrEmpty("sorter"), isItem),
                TargetSettings.parse(config.sectionOrEmpty("targets")),
                DeliverySettings.parse(config.sectionOrEmpty("delivery"), isItem),
                InspectSettings.parse(config.sectionOrEmpty("inspect")));
    }
}
