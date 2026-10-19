package com.cesarcosmico.framesort.config;

import org.bukkit.Color;

/** The colours a sorter's or pad's pulse fades between when it sends. */
public record ActivitySettings(Color from, Color to) {

    static ActivitySettings parse(ConfigReader reader) {
        return new ActivitySettings(
                reader.color("from", Color.fromRGB(0xF7B733)),
                reader.color("to", Color.fromRGB(0xFC4A1A)));
    }
}
