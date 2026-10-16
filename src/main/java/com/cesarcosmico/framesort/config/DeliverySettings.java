package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.model.ItemReference;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

public record DeliverySettings(int maxDistance, boolean insertIntoContainers,
                               @Nullable ItemReference defaultTargetItem) {

    private static final ItemReference DEFAULT_TARGET = new ItemReference.Vanilla(Material.CARROT_ON_A_STICK);

    static DeliverySettings parse(ConfigReader reader, Predicate<Material> isItem) {
        return new DeliverySettings(
                reader.integer("max-distance", 64, 1, 512),
                reader.bool("insert-into-containers", true),
                defaultTarget(reader, isItem));
    }

    private static @Nullable ItemReference defaultTarget(ConfigReader reader, Predicate<Material> isItem) {
        String text = reader.string("default-target-item", "CARROT_ON_A_STICK");
        if (text.isBlank()) {
            return null;
        }
        ItemReference item = ItemReferences.parse(text,
                problem -> reader.warn("default-target-item", problem + ", using CARROT_ON_A_STICK"));
        if (item instanceof ItemReference.Vanilla(Material material) && !isItem.test(material)) {
            reader.warn("default-target-item", material + " is not an item, using CARROT_ON_A_STICK");
            return DEFAULT_TARGET;
        }
        return item == null ? DEFAULT_TARGET : item;
    }
}
