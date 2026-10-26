package com.cesarcosmico.framesort.model;

import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

public sealed interface ItemReference {

    String CRAFT_ENGINE = "craftengine";

    record Vanilla(Material material) implements ItemReference {
    }

    record Custom(String reference) implements ItemReference {
    }

    /**
     * Whether an item or block of {@code type}, whose custom reference is {@code customId} ({@code null} for a vanilla
     * one), is this.
     */
    default boolean matches(Material type, @Nullable String customId) {
        return switch (this) {
            case Vanilla vanilla -> customId == null && type == vanilla.material();
            case Custom custom -> custom.reference().equals(customId);
        };
    }
}
