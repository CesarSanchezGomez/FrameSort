package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.model.ItemReference;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

public record DeliverySettings(int maxDistance, boolean insertIntoContainers,
                               @Nullable ItemReference defaultTargetItem) {

    private static final ItemReference.Vanilla DEFAULT_TARGET = new ItemReference.Vanilla(Material.CARROT_ON_A_STICK);

    static DeliverySettings parse(ConfigReader reader, Predicate<Material> isItem, boolean craftEngine) {
        return new DeliverySettings(
                reader.integer("max-distance", 64, 1, 512),
                reader.bool("insert-into-containers", true),
                defaultTarget(reader, isItem, craftEngine));
    }

    private static @Nullable ItemReference defaultTarget(ConfigReader reader, Predicate<Material> isItem,
                                                         boolean craftEngine) {
        String text = reader.string("default-target-item", DEFAULT_TARGET.material().name());
        return text.isBlank() ? null
                : ItemReferences.item(reader, "default-target-item", text, DEFAULT_TARGET, isItem, craftEngine);
    }
}
