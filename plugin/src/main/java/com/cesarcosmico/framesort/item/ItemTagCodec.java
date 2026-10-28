package com.cesarcosmico.framesort.item;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import java.util.Set;

public final class ItemTagCodec {

    private static final Set<DataComponentType> CHANGED_BY_TAG = Set.of(DataComponentTypes.CUSTOM_NAME,
            DataComponentTypes.LORE, DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);

    private ItemTagCodec() {
    }

    public static void apply(ItemStack item, NamespacedKey tag, ItemLook look, TagResolver... resolvers) {
        if (read(item) != null) {
            // Otherwise the first tag's look would be saved as the item's own.
            clear(item);
        }
        ItemStack own = ItemStack.of(item.getType());
        own.copyDataFrom(item, CHANGED_BY_TAG::contains);
        byte[] snapshot = own.serializeAsBytes();
        ItemOverlay.applyBelowOwnLore(item, look, resolvers);
        item.editPersistentDataContainer(pdc -> {
            pdc.set(Keys.ITEM_TAG, PersistentDataType.STRING, tag.asString());
            pdc.set(Keys.ITEM_TAG_ORIGINAL, PersistentDataType.BYTE_ARRAY, snapshot);
        });
    }

    /** Takes the tag off and gives the item back its own look, when the tag saved it. */
    public static void clear(ItemStack item) {
        byte[] snapshot = item.getPersistentDataContainer().get(Keys.ITEM_TAG_ORIGINAL, PersistentDataType.BYTE_ARRAY);
        // A tag key alone comes from outside FrameSort (a /give, another plugin copying part of the data).
        if (snapshot != null) {
            ItemStack own = ItemStack.deserializeBytes(snapshot);
            // resetData, never unsetData: on a nether star that would remove its own glint, and it would stop stacking.
            CHANGED_BY_TAG.forEach(item::resetData);
            item.copyDataFrom(own, CHANGED_BY_TAG::contains);
        }
        item.editPersistentDataContainer(pdc -> {
            pdc.remove(Keys.ITEM_TAG);
            pdc.remove(Keys.ITEM_TAG_ORIGINAL);
        });
    }

    public static @Nullable NamespacedKey read(ItemStack item) {
        String tag = item.getPersistentDataContainer().get(Keys.ITEM_TAG, PersistentDataType.STRING);
        return tag == null ? null : NamespacedKey.fromString(tag);
    }
}
