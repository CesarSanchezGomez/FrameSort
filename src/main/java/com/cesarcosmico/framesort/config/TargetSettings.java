package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.item.ItemLook;
import com.cesarcosmico.framesort.model.TargetRegistration;
import org.bukkit.entity.EntityType;

import java.util.Set;

/** {@code taggedItem} is the look /framesort tag apply puts on the item in the player's hand. */
public record TargetSettings(TargetRegistration registration, Set<EntityType> frameTypes, ItemLook taggedItem) {

    public TargetSettings {
        frameTypes = Set.copyOf(frameTypes);
    }

    static TargetSettings parse(ConfigReader reader) {
        return new TargetSettings(
                reader.enumValue("registration", TargetRegistration.class, TargetRegistration.MANUAL),
                SorterSettings.frameTypes(reader),
                ItemLookParser.look(reader.sectionOrEmpty("tagged-item")));
    }
}
