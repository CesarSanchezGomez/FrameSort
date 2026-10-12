package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.model.TargetRegistration;
import org.bukkit.entity.EntityType;

import java.util.Set;

public record TargetSettings(TargetRegistration registration, Set<EntityType> frameTypes, String taggedName,
                             boolean taggedGlint) {

    public TargetSettings {
        frameTypes = Set.copyOf(frameTypes);
    }

    static TargetSettings parse(ConfigReader reader) {
        ConfigReader tagged = reader.sectionOrEmpty("tagged-item");
        return new TargetSettings(
                reader.enumValue("registration", TargetRegistration.class, TargetRegistration.MANUAL),
                SorterSettings.frameTypes(reader),
                tagged.string("name", "<gradient:#F7B733:#FC4A1A>#<tag></gradient>"),
                tagged.bool("glint", true));
    }
}
