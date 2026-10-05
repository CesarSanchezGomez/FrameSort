package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.model.FramePosition;
import com.cesarcosmico.framesort.model.TargetRegistration;
import org.bukkit.entity.EntityType;

import java.util.EnumSet;
import java.util.Set;

/** The {@code targets} section: which item frames receive items. */
public record TargetSettings(TargetRegistration registration, Set<FramePosition> positions,
                             Set<EntityType> frameTypes) {

    public TargetSettings {
        positions = Set.copyOf(positions);
        frameTypes = Set.copyOf(frameTypes);
    }

    static TargetSettings parse(ConfigReader reader) {
        return new TargetSettings(
                reader.enumValue("registration", TargetRegistration.class, TargetRegistration.MANUAL),
                reader.enumSet("positions", FramePosition.class, EnumSet.allOf(FramePosition.class)),
                SorterSettings.frameTypes(reader, "frame-types"));
    }
}
