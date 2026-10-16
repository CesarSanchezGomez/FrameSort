package com.cesarcosmico.framesort.model;

import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

/**
 * An item or block named in the config: a vanilla material, or a CraftEngine one by its reference, written
 * {@code craftengine:<namespace>:<id>} as tags.yml and the CustomItemResolver write it.
 */
public sealed interface ItemReference {

    /** The plugin name that starts a CraftEngine reference. */
    String CRAFT_ENGINE = "craftengine";

    record Vanilla(Material material) implements ItemReference {
    }

    record Custom(String reference) implements ItemReference {
    }

    /**
     * Whether an item or block of {@code type}, whose custom reference is {@code customId} ({@code null} for a vanilla
     * one), is this. A custom item or block is only a vanilla material underneath, so it is never that material.
     */
    default boolean matches(Material type, @Nullable String customId) {
        return switch (this) {
            case Vanilla vanilla -> customId == null && type == vanilla.material();
            case Custom custom -> custom.reference().equals(customId);
        };
    }
}
