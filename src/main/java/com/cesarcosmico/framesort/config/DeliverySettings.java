package com.cesarcosmico.framesort.config;

import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

public record DeliverySettings(int maxDistance, boolean insertIntoContainers,
                               @Nullable Material defaultTargetItem) {

    static DeliverySettings parse(ConfigReader reader, Predicate<Material> isItem) {
        Material defaultTarget = reader.material("default-target-item", Material.CARROT_ON_A_STICK);
        if (defaultTarget != null && !isItem.test(defaultTarget)) {
            reader.warn("default-target-item", defaultTarget + " is not an item, using CARROT_ON_A_STICK");
            defaultTarget = Material.CARROT_ON_A_STICK;
        }
        return new DeliverySettings(
                reader.integer("max-distance", 64, 1, 512),
                reader.bool("insert-into-containers", true),
                defaultTarget);
    }
}
