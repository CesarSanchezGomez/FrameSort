package com.cesarcosmico.framesort.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.function.Consumer;

/** {@code config.yml}, parsed once per load or reload into an immutable snapshot. */
public record FrameSortSettings(String language, SorterSettings sorter, TargetSettings targets,
                                DeliverySettings delivery, InspectSettings inspect) {

    public static FrameSortSettings parse(ConfigurationSection root, Consumer<String> warn) {
        ConfigReader reader = new ConfigReader(root, "config.yml", warn);
        return new FrameSortSettings(
                reader.string("language", "en_US"),
                SorterSettings.parse(reader.sectionOrEmpty("sorter")),
                TargetSettings.parse(reader.sectionOrEmpty("targets")),
                DeliverySettings.parse(reader.sectionOrEmpty("delivery")),
                InspectSettings.parse(reader.sectionOrEmpty("inspect")));
    }
}
