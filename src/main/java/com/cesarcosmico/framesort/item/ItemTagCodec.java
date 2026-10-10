package com.cesarcosmico.framesort.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

// The tag lives in the item's data, not in its name, so renaming a tagged item never changes what it accepts.
public final class ItemTagCodec {

    private ItemTagCodec() {
    }

    public static void apply(ItemStack item, NamespacedKey tag, Component name, boolean glint) {
        item.editPersistentDataContainer(pdc -> pdc.set(Keys.ITEM_TAG, PersistentDataType.STRING, tag.asString()));
        item.setData(DataComponentTypes.CUSTOM_NAME,
                name.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        if (glint) {
            item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
    }

    public static void clear(ItemStack item) {
        item.editPersistentDataContainer(pdc -> pdc.remove(Keys.ITEM_TAG));
        item.unsetData(DataComponentTypes.CUSTOM_NAME);
        item.unsetData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
    }

    public static @Nullable NamespacedKey read(ItemStack item) {
        String tag = item.getPersistentDataContainer().get(Keys.ITEM_TAG, PersistentDataType.STRING);
        return tag == null ? null : NamespacedKey.fromString(tag);
    }
}
