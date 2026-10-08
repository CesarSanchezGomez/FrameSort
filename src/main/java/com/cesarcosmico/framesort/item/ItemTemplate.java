package com.cesarcosmico.framesort.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record ItemTemplate(Material material, @Nullable String name, List<String> lore, boolean glint,
                           @Nullable NamespacedKey itemModel) {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public ItemTemplate {
        lore = List.copyOf(lore);
    }

    public ItemStack create(NamespacedKey marker, String value, int amount) {
        ItemStack item = ItemStack.of(material, amount);
        if (name != null) {
            item.setData(DataComponentTypes.ITEM_NAME, MINI_MESSAGE.deserialize(name));
        }
        if (!lore.isEmpty()) {
            item.setData(DataComponentTypes.LORE, ItemLore.lore(lore.stream().map(ItemTemplate::loreLine).toList()));
        }
        if (glint) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        if (itemModel != null) {
            item.setData(DataComponentTypes.ITEM_MODEL, itemModel);
        }
        item.editPersistentDataContainer(pdc -> pdc.set(marker, PersistentDataType.STRING, value));
        return item;
    }

    public static @Nullable String marker(@Nullable ItemStack item, NamespacedKey marker) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        return item.getPersistentDataContainer().get(marker, PersistentDataType.STRING);
    }

    // Lore is italic by default in vanilla; only keep the italics the owner asked for.
    private static Component loreLine(String line) {
        return MINI_MESSAGE.deserialize(line).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
