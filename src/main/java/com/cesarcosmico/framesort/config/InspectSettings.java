package com.cesarcosmico.framesort.config;

import org.bukkit.Material;

/** The {@code inspect} section: the tool, target highlighting, list pages and live tracing. */
public record InspectSettings(Material tool, int highlightSeconds, int pageSize, int traceRadius,
                              int traceDefaultSeconds, int traceMaxSeconds) {

    static InspectSettings parse(ConfigReader reader) {
        int traceMax = reader.integer("trace-max-seconds", 600, 1, 3600);
        return new InspectSettings(
                reader.requiredMaterial("tool", Material.STICK),
                reader.integer("highlight-seconds", 10, 1, 120),
                reader.integer("page-size", 10, 1, 50),
                reader.integer("trace-radius", 32, 1, 256),
                Math.min(traceMax, reader.integer("trace-default-seconds", 60, 1, 3600)),
                traceMax);
    }
}
