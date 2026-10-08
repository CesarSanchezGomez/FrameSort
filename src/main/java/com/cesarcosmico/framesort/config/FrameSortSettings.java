package com.cesarcosmico.framesort.config;

public record FrameSortSettings(String language, SorterSettings sorter, TargetSettings targets,
                                DeliverySettings delivery, InspectSettings inspect) {

    public static FrameSortSettings parse(ConfigReader config) {
        String language = config.string("language", "en_US");
        if (language.isBlank()) {
            config.warn("language", "empty, using en_US");
            language = "en_US";
        }
        return new FrameSortSettings(
                language,
                SorterSettings.parse(config.sectionOrEmpty("sorter")),
                TargetSettings.parse(config.sectionOrEmpty("targets")),
                DeliverySettings.parse(config.sectionOrEmpty("delivery")),
                InspectSettings.parse(config.sectionOrEmpty("inspect")));
    }
}
