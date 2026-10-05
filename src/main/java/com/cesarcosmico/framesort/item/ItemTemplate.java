package com.cesarcosmico.framesort.item;

import com.cesarcosmico.framesort.config.ConfigReader;
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

/** An item FrameSort hands out (the sorter activator, a pad block): how it looks, plus a persistent marker. */
public record ItemTemplate(Material material, @Nullable String name, List<String> lore, boolean glint,
                           @Nullable NamespacedKey itemModel) {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public ItemTemplate {
        lore = List.copyOf(lore);
    }

    /** Reads {@code name}, {@code lore}, {@code glint} and {@code item-model}; the material is decided by the caller. */
    public static ItemTemplate parse(ConfigReader reader, Material material) {
        String name = reader.string("name", "");
        return new ItemTemplate(material, name.isBlank() ? null : name, reader.strings("lore"),
                reader.bool("glint", false), reader.key("item-model"));
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

    /** The marker value FrameSort wrote on this item, or {@code null} for any other item. */
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
