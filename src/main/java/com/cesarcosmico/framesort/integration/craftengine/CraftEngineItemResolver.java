package com.cesarcosmico.framesort.integration.craftengine;

import com.cesarcosmico.framesort.service.CustomItemResolver;
import net.kyori.adventure.text.Component;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** Created only when CraftEngine is enabled, so its classes are never loaded otherwise. */
public final class CraftEngineItemResolver implements CustomItemResolver {

    private static final String PREFIX = "craftengine:";

    @Override
    public @Nullable String id(ItemStack item) {
        // Reads the ID stored on the item, not CraftEngine's loaded item list, which is empty while it starts up or
        // reloads: an item never changes destination during a /ce reload.
        Key id = CraftEngineItems.getCustomItemId(item);
        return id == null ? null : PREFIX + id.asString();
    }

    @Override
    public @Nullable Component name(String reference) {
        BukkitItemDefinition item = CraftEngineItems.byId(reference.substring(PREFIX.length()));
        // CraftEngine translates this name for each player as the message goes out, as it does in inventories.
        return item == null ? null : item.buildBukkitItem().effectiveName();
    }

    @Override
    public boolean isCustomBlock(Block block) {
        // True for any of CraftEngine's block slots, also while a /ce reload has them unbound.
        return CraftEngineBlocks.isCustomBlock(block);
    }
}
