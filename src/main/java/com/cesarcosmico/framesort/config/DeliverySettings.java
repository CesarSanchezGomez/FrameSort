package com.cesarcosmico.framesort.config;

import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

/**
 * The {@code delivery} section.
 *
 * @param insertIntoContainers put items straight into the container behind a target; when false every target
 *                             only receives items dropped in front of it
 * @param defaultTargetItem    frames holding this item take whatever matches nothing else; {@code null} = off
 */
public record DeliverySettings(int maxDistance, boolean insertIntoContainers,
                               @Nullable Material defaultTargetItem) {

    static DeliverySettings parse(ConfigReader reader) {
        return new DeliverySettings(
                reader.integer("max-distance", 64, 1, 512),
                reader.bool("insert-into-containers", true),
                reader.material("default-target-item", Material.CARROT_ON_A_STICK));
    }

    public long maxDistanceSquared() {
        return (long) maxDistance * maxDistance;
    }
}
