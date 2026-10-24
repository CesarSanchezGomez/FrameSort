package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.item.ItemLook;
import org.bukkit.entity.EntityType;

import java.util.Set;
import java.util.stream.Collectors;

/** {@code taggedItem} is the look /framesort tag apply puts on the item in the player's hand. */
public record TargetSettings(Set<EntityType> frameTypes, ItemLook taggedItem) {

    private static final Set<EntityType> ITEM_FRAMES = Set.of(EntityType.ITEM_FRAME, EntityType.GLOW_ITEM_FRAME);

    public TargetSettings {
        frameTypes = Set.copyOf(frameTypes);
    }

    static TargetSettings parse(ConfigReader reader) {
        return new TargetSettings(
                frameTypes(reader),
                ItemLookParser.look(reader.sectionOrEmpty("tagged-item")));
    }

    private static Set<EntityType> frameTypes(ConfigReader reader) {
        Set<EntityType> types = reader.enumSet("frame-types", EntityType.class, ITEM_FRAMES);
        if (!ITEM_FRAMES.containsAll(types)) {
            reader.warn("frame-types", "only ITEM_FRAME and GLOW_ITEM_FRAME are item frames; other types are ignored");
            types = types.stream().filter(ITEM_FRAMES::contains).collect(Collectors.toSet());
        }
        return types.isEmpty() ? ITEM_FRAMES : types;
    }
}
