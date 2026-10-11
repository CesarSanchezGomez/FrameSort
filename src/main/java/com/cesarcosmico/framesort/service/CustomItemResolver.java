package com.cesarcosmico.framesort.service;

import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** The ID a custom item plugin gave an item, such as {@code default:ruby}; {@link #NONE} when none is installed. */
@FunctionalInterface
public interface CustomItemResolver {

    CustomItemResolver NONE = item -> null;

    /** @return the item's custom ID, or {@code null} for a vanilla item */
    @Nullable String id(ItemStack item);
}
