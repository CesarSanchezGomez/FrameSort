package com.cesarcosmico.framesort.config;

import org.bukkit.Color;
import org.bukkit.Material;

public record InspectSettings(Material tool, int highlightSeconds, int pageSize, int traceRadius,
                              int traceDefaultSeconds, int traceMaxSeconds, Colors colors) {

    public record Colors(Color container, Color dropped, Color lava) {
    }

    static InspectSettings parse(ConfigReader reader) {
        int traceMax = reader.integer("trace-max-seconds", 600, 1, 3600);
        ConfigReader colors = reader.sectionOrEmpty("colors");
        return new InspectSettings(
                reader.requiredMaterial("tool", Material.STICK),
                reader.integer("highlight-seconds", 10, 1, 120),
                reader.integer("page-size", 8, 1, 50),
                reader.integer("trace-radius", 32, 1, 256),
                Math.min(traceMax, reader.integer("trace-default-seconds", 60, 1, 3600)),
                traceMax,
                new Colors(
                        colors.color("container", Color.fromRGB(0x55FF55)),
                        colors.color("dropped", Color.fromRGB(0xFFFF55)),
                        colors.color("lava", Color.fromRGB(0xFF5555))));
    }
}
