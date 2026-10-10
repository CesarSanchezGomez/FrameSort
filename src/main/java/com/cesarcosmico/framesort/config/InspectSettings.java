package com.cesarcosmico.framesort.config;

import org.bukkit.Color;

public record InspectSettings(int highlightSeconds, int pageSize, int traceRadius,
                              int traceDefaultSeconds, int traceMaxSeconds, Colors colors) {

    public record Colors(Color container, Color dropped, Color lava) {
    }

    static InspectSettings parse(ConfigReader reader) {
        int traceMax = reader.integer("trace-max-seconds", 600, 1, 3600);
        int traceDefault = reader.integer("trace-default-seconds", 60, 1, 3600);
        if (traceDefault > traceMax) {
            reader.warn("trace-default-seconds", traceDefault + " is above trace-max-seconds, using " + traceMax);
            traceDefault = traceMax;
        }
        ConfigReader colors = reader.sectionOrEmpty("colors");
        return new InspectSettings(
                reader.integer("highlight-seconds", 5, 1, 120),
                reader.integer("page-size", 8, 1, 50),
                reader.integer("trace-radius", 32, 1, 256),
                traceDefault,
                traceMax,
                new Colors(
                        colors.color("container", Color.fromRGB(0x55FF55)),
                        colors.color("dropped", Color.fromRGB(0xFFFF55)),
                        colors.color("lava", Color.fromRGB(0xFF5555))));
    }
}
