package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.model.Delivery;
import com.cesarcosmico.framesort.model.ItemReference;
import net.kyori.adventure.text.Component;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

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

    /**
     * The block's name as players see it: a vanilla block's translation, or the name of the item that places the
     * plugin's block, {@code null} when {@link #blockItem} can't build it.
     */
    default @Nullable Component blockName(ItemReference block) {
        return switch (block) {
            case ItemReference.Vanilla vanilla -> Component.translatable(vanilla.material());
            case ItemReference.Custom custom -> {
                ItemStack item = blockItem(custom, 1);
                yield item == null ? null : item.effectiveName();
            }
        };
    }

    /** Whether the plugin's block at {@code block} stores items that a hopper may put in. */
    default boolean isContainer(Block block) {
        return false;
    }

    /**
     * Puts {@code items} into the plugin's block at {@code block} as a hopper would and returns how many it didn't
     * take, or {@link Delivery#GONE} when that block stores no items ({@link #isContainer}).
     */
    default int store(Block block, ItemStack items) {
        return Delivery.GONE;
    }
}
