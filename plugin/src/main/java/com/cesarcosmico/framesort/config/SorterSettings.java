package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.item.ItemLook;
import com.cesarcosmico.framesort.item.ItemTemplate;
import com.cesarcosmico.framesort.model.ItemReference;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public record SorterSettings(ItemReference activator, ItemLook activatorLook, Map<String, String> activatorComponents,
                             boolean requireMarked, Set<EntityType> frameTypes, @Nullable String customName,
                             int range, int tickRate, boolean disableWhenPowered, boolean hideFrame,
                             boolean showActivity) {

    private static final Set<EntityType> ITEM_FRAMES = Set.of(EntityType.ITEM_FRAME, EntityType.GLOW_ITEM_FRAME);
    private static final ItemReference.Vanilla DEFAULT_ACTIVATOR = new ItemReference.Vanilla(Material.HOPPER);

    public SorterSettings {
        activatorComponents = Collections.unmodifiableMap(new LinkedHashMap<>(activatorComponents));
        frameTypes = Set.copyOf(frameTypes);
    }

    static SorterSettings parse(ConfigReader reader, Predicate<Material> isItem,
                                Function<String, @Nullable String> problem, boolean craftEngine) {
        ConfigReader activator = reader.sectionOrEmpty("activator");
        ItemReference item = ItemReferences.item(activator, "material",
                activator.string("material", DEFAULT_ACTIVATOR.material().name()), DEFAULT_ACTIVATOR, isItem,
                craftEngine);
        ItemLook look;
        Map<String, String> components;
        if (item instanceof ItemReference.Vanilla(Material material)) {
            ItemTemplate template = ItemLookParser.template(activator, material, problem);
            look = template.look();
            components = template.components();
        } else {
            ItemReferences.ignoreComponents(activator);
            look = ItemLookParser.look(activator);
            components = Map.of();
        }
        String customName = reader.string("custom-name", "");
        return new SorterSettings(
                item,
                look,
                components,
                activator.bool("require-marked", false),
                frameTypes(reader),
                customName.isBlank() ? null : customName,
                reader.integer("range", 64, 1, 512),
                reader.integer("tick-rate", 20, 1, 1200),
                reader.bool("disable-when-powered", false),
                reader.bool("hide-frame", true),
                reader.bool("show-activity", true));
    }

    static Set<EntityType> frameTypes(ConfigReader reader) {
        Set<EntityType> types = reader.enumSet("frame-types", EntityType.class, ITEM_FRAMES);
        if (!ITEM_FRAMES.containsAll(types)) {
            reader.warn("frame-types", "only ITEM_FRAME and GLOW_ITEM_FRAME are item frames; other types are ignored");
            types = types.stream().filter(ITEM_FRAMES::contains).collect(Collectors.toSet());
        }
        return types.isEmpty() ? ITEM_FRAMES : types;
    }
}
