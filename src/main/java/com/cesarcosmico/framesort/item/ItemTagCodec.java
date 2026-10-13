package com.cesarcosmico.framesort.item;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import java.util.Set;

// The tag lives in the item's data, not in its name, so renaming a tagged item never changes what it accepts.
public final class ItemTagCodec {

    // What a tag changes on the item, and so what it gives back when it comes off.
    private static final Set<DataComponentType> LOOK = Set.of(DataComponentTypes.CUSTOM_NAME,
            DataComponentTypes.LORE, DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);

    private ItemTagCodec() {
    }

    /** The item keeps a copy of the look it had, so {@link #clear} can give it back exactly. */
    public static void apply(ItemStack item, NamespacedKey tag, ItemLook look, TagResolver... resolvers) {
        if (read(item) != null) {
            // Otherwise the first tag's look would be saved as the item's own.
            clear(item);
        }
        ItemStack own = ItemStack.of(item.getType());
        own.copyDataFrom(item, LOOK::contains);
        byte[] snapshot = own.serializeAsBytes();
        ItemOverlay.apply(item, look, resolvers);
        item.editPersistentDataContainer(pdc -> {
            pdc.set(Keys.ITEM_TAG, PersistentDataType.STRING, tag.asString());
            pdc.set(Keys.ITEM_TAG_ORIGINAL, PersistentDataType.BYTE_ARRAY, snapshot);
        });
    }

    /** Takes the tag off and gives the item back the name, lore and glint it had when it was tagged. */
    public static void clear(ItemStack item) {
        byte[] snapshot = item.getPersistentDataContainer().get(Keys.ITEM_TAG_ORIGINAL, PersistentDataType.BYTE_ARRAY);
        ItemStack own = ItemStack.deserializeBytes(snapshot);
        // resetData, never unsetData: on a nether star that would remove its own glint, and it would stop stacking.
        LOOK.forEach(item::resetData);
        item.copyDataFrom(own, LOOK::contains);
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
