package com.cesarcosmico.framesort.service;

import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * How tags.yml refers to an item from a custom item plugin: the plugin's name in lowercase, {@code :} and the ID that
 * plugin gave it, such as {@code craftengine:default:ruby}; also whether a placed block is one of that plugin's.
 * {@link #NONE} when no such plugin is installed.
 */
@FunctionalInterface
public interface CustomItemResolver {

    CustomItemResolver NONE = item -> null;

    /** @return the item's reference, or {@code null} for a vanilla item */
    @Nullable String id(ItemStack item);

    /** @return the name the plugin gives the item of {@code reference}, or {@code null} if it doesn't know it */
    default @Nullable Component name(String reference) {
        return null;
    }

    /** Whether {@code block} is one of the plugin's own blocks, whatever vanilla block it shows as to the server. */
    default boolean isCustomBlock(Block block) {
        return false;
    }
}
