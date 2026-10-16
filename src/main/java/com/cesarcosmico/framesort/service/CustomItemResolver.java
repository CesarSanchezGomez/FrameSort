package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.model.ItemReference;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What FrameSort needs from a custom item plugin: an item's reference as tags.yml writes it (the plugin's name in
 * lowercase, {@code :} and the ID that plugin gave it, such as {@code craftengine:default:ruby}) and its name, which
 * placed blocks are the plugin's and which one, and the items the plugin builds for a reference or a block.
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

    /**
     * The item of {@code reference} as the plugin builds it, or {@code null} for an ID it doesn't know or while it
     * reloads.
     */
    default @Nullable ItemStack item(ItemReference.Custom reference, int amount) {
        return null;
    }

    /**
     * The reference to the plugin's block at {@code block}, or {@code null} for a vanilla block and, while the plugin
     * reloads, for one of its own ({@link #isCustomBlock} still tells those apart).
     */
    default @Nullable String blockId(Block block) {
        return null;
    }

    /** The item that places the plugin's {@code block}, as {@link #item} builds it, or {@code null}. */
    default @Nullable ItemStack blockItem(ItemReference.Custom block, int amount) {
        return null;
    }
}
