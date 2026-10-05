package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.item.ItemTemplate;
import com.cesarcosmico.framesort.item.Keys;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The {@code sorter} section.
 *
 * @param requireMarked when true only the activator given by FrameSort works; otherwise any item of its material
 * @param customName    MiniMessage name given to the dispenser, or {@code null} to leave it unnamed
 */
public record SorterSettings(ItemTemplate activator, boolean requireMarked, List<NamespacedKey> recipes,
                             Set<EntityType> frameTypes, @Nullable String customName, int tickRate,
                             boolean disableWhenPowered, boolean hideFrame, boolean showActivity) {

    static final Set<EntityType> ITEM_FRAMES = Set.of(EntityType.ITEM_FRAME, EntityType.GLOW_ITEM_FRAME);

    public SorterSettings {
        recipes = List.copyOf(recipes);
        frameTypes = Set.copyOf(frameTypes);
    }

    static SorterSettings parse(ConfigReader reader) {
        ConfigReader activator = reader.sectionOrEmpty("activator");
        Material material = activator.requiredMaterial("material", Material.ENDER_EYE);
        String customName = reader.string("custom-name", "<gray>Item Sorter");
        return new SorterSettings(
                ItemTemplate.parse(activator, material),
                activator.bool("require-marked", false),
                reader.keys("recipes"),
                frameTypes(reader, "frame-types"),
                customName.isBlank() ? null : customName,
                reader.integer("tick-rate", 20, 1, 1200),
                reader.bool("disable-when-powered", false),
                reader.bool("hide-frame", true),
                reader.bool("show-activity", true));
    }

    public boolean isActivator(@Nullable ItemStack item) {
        if (item == null || item.getType() != activator.material()) {
            return false;
        }
        return !requireMarked || ItemTemplate.marker(item, Keys.ACTIVATOR) != null;
    }

    static Set<EntityType> frameTypes(ConfigReader reader, String path) {
        Set<EntityType> types = reader.enumSet(path, EntityType.class, ITEM_FRAMES);
        if (!ITEM_FRAMES.containsAll(types)) {
            reader.warn(path, "only ITEM_FRAME and GLOW_ITEM_FRAME are item frames; other types are ignored");
            types = types.stream().filter(ITEM_FRAMES::contains).collect(Collectors.toSet());
        }
        return types.isEmpty() ? ITEM_FRAMES : types;
    }
}
