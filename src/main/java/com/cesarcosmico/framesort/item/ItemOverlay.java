package com.cesarcosmico.framesort.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * FrameSort's name, lore and glint over an item it doesn't build: a player's (a tag) or CraftEngine's. The name goes
 * in as custom_name, over the item's own.
 */
public final class ItemOverlay {

    private ItemOverlay() {
    }

    /** On an item FrameSort hands out: its lore, if it has one, stands in for the item's own. */
    public static ItemStack apply(ItemStack item, ItemLook look, TagResolver... resolvers) {
        return apply(item, look, List.of(), resolvers);
    }

    /** On a player's item: its lore goes below the item's own lines, which still say something about it. */
    public static ItemStack applyBelowOwnLore(ItemStack item, ItemLook look, TagResolver... resolvers) {
        ItemLore own = item.getData(DataComponentTypes.LORE);
        return apply(item, look, own == null ? List.of() : own.lines(), resolvers);
    }

    private static ItemStack apply(ItemStack item, ItemLook look, List<Component> kept, TagResolver... resolvers) {
        Component name = look.renderName(resolvers);
        if (name != null) {
            item.setData(DataComponentTypes.CUSTOM_NAME, name);
        }
        if (!look.lore().isEmpty()) {
            List<Component> lines = new ArrayList<>(kept);
            lines.addAll(look.renderLore(resolvers));
            item.setData(DataComponentTypes.LORE, ItemLore.lore(lines));
        }
        if (look.glint()) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return item;
    }
}
