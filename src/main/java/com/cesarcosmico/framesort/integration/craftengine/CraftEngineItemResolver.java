package com.cesarcosmico.framesort.integration.craftengine;

import com.cesarcosmico.framesort.service.CustomItemResolver;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** Created only when CraftEngine is enabled, so its classes are never loaded otherwise. */
public final class CraftEngineItemResolver implements CustomItemResolver {

    @Override
    public @Nullable String id(ItemStack item) {
        // Reads the ID stored on the item, not CraftEngine's loaded item list, which is empty while it starts up or
        // reloads: an item never changes destination during a /ce reload.
        Key id = CraftEngineItems.getCustomItemId(item);
        return id == null ? null : id.asString();
    }
}
