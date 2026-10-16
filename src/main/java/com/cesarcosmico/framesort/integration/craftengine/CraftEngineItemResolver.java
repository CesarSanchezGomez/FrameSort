package com.cesarcosmico.framesort.integration.craftengine;

import com.cesarcosmico.framesort.model.ItemReference;
import com.cesarcosmico.framesort.service.CustomItemResolver;
import net.kyori.adventure.text.Component;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/** Created only when CraftEngine is enabled, so its classes are never loaded otherwise. */
public final class CraftEngineItemResolver implements CustomItemResolver {

    private static final String PREFIX = ItemReference.CRAFT_ENGINE + ":";

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
    public @Nullable ItemStack item(ItemReference.Custom reference, int amount) {
        // Null before CraftEngine has loaded its items, during /ce reload, and for an ID it doesn't know.
        BukkitItemDefinition item = CraftEngineItems.byId(reference.reference().substring(PREFIX.length()));
        if (item == null) {
            return null;
        }
        ItemStack built = item.buildBukkitItem();
        built.setAmount(amount);
        return built;
    }

    @Override
    public @Nullable String blockId(Block block) {
        ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
        // Null for a vanilla block, and for a CraftEngine one while /ce reload has it unbound.
        return state == null || state.isEmpty() ? null : PREFIX + state.owner().value().id().asString();
    }

    @Override
    public @Nullable ItemStack blockItem(ItemReference.Custom block, int amount) {
        BlockDefinition definition = CraftEngineBlocks.byId(Key.of(block.reference().substring(PREFIX.length())));
        // The block's own ID unless its settings name another item; there may be no item with that ID.
        Key item = definition == null ? null : definition.defaultState().settings().itemId();
        return item == null ? null : item(new ItemReference.Custom(PREFIX + item.asString()), amount);
    }

    @Override
    public boolean isCustomBlock(Block block) {
        // True for any of CraftEngine's block slots, also while a /ce reload has them unbound.
        return CraftEngineBlocks.isCustomBlock(block);
    }
}
