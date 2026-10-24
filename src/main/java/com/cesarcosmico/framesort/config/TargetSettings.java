package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.item.ItemLook;
import org.bukkit.entity.EntityType;

import java.util.Set;

/** {@code taggedItem} is the look /framesort tag apply puts on the item in the player's hand. */
public record TargetSettings(Set<EntityType> frameTypes, ItemLook taggedItem) {

    public TargetSettings {
        frameTypes = Set.copyOf(frameTypes);
    }

    static TargetSettings parse(ConfigReader reader) {
        return new TargetSettings(
                SorterSettings.frameTypes(reader),
                ItemLookParser.look(reader.sectionOrEmpty("tagged-item")));
    }
}
