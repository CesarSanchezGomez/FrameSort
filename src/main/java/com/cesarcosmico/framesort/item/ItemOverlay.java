package com.cesarcosmico.framesort.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** FrameSort's name, lore and glint over an item it doesn't build: a player's (a tag) or CraftEngine's. */
public final class ItemOverlay {

    private ItemOverlay() {
    }

    /** The name goes in as custom_name, over the item's own, and the lore below the item's own lines. */
    public static ItemStack apply(ItemStack item, ItemLook look, TagResolver... resolvers) {
        Component name = look.renderName(resolvers);
        if (name != null) {
            item.setData(DataComponentTypes.CUSTOM_NAME, name);
        }
        if (!look.lore().isEmpty()) {
            ItemLore own = item.getData(DataComponentTypes.LORE);
            List<Component> lines = new ArrayList<>(own == null ? List.of() : own.lines());
            lines.addAll(look.renderLore(resolvers));
            item.setData(DataComponentTypes.LORE, ItemLore.lore(lines));
        }
        if (look.glint()) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return item;
    }
}
