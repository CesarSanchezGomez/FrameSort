package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.item.ItemLook;
import org.bukkit.entity.EntityType;

import java.util.Set;

public record TargetSettings(Set<EntityType> frameTypes, ItemLook taggedItemLook) {

    public TargetSettings {
        frameTypes = Set.copyOf(frameTypes);
    }

    static TargetSettings parse(ConfigReader reader) {
        return new TargetSettings(
                SorterSettings.frameTypes(reader),
                ItemLookParser.look(reader.sectionOrEmpty("tagged-item")));
    }
}
