package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.model.FramePosition;
import com.cesarcosmico.framesort.model.TargetRegistration;
import org.bukkit.entity.EntityType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public record TargetSettings(TargetRegistration registration, Set<FramePosition> positions,
                             Set<EntityType> frameTypes, String taggedName, boolean taggedGlint) {

    public TargetSettings {
        positions = Set.copyOf(positions);
        frameTypes = Set.copyOf(frameTypes);
    }

    static TargetSettings parse(ConfigReader reader) {
        ConfigReader tagged = reader.sectionOrEmpty("tagged-item");
        return new TargetSettings(
                reader.enumValue("registration", TargetRegistration.class, TargetRegistration.MANUAL),
                positions(reader),
                SorterSettings.frameTypes(reader),
                tagged.string("name", "<gradient:#F7B733:#FC4A1A>#<tag></gradient>"),
                tagged.bool("glint", true));
    }

    public boolean allows(Set<FramePosition> frame) {
        return !Collections.disjoint(positions, frame);
    }

    private static Set<FramePosition> positions(ConfigReader reader) {
        Set<FramePosition> front = EnumSet.of(FramePosition.FRONT);
        if (!reader.isSet("positions")) {
            return front;
        }
        Set<FramePosition> positions = EnumSet.noneOf(FramePosition.class);
        for (String name : reader.strings("positions")) {
            String key = name.trim().toUpperCase(Locale.ROOT);
            if (key.equals("SIDES")) {
                positions.add(FramePosition.LEFT);
                positions.add(FramePosition.RIGHT);
                continue;
            }
            try {
                positions.add(FramePosition.valueOf(key));
            } catch (IllegalArgumentException e) {
                reader.warn("positions", "unknown value '" + name + "', ignored");
            }
        }
        if (positions.isEmpty()) {
            reader.warn("positions", "no valid values, using FRONT");
            return front;
        }
        return positions;
    }
}
