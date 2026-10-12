package com.cesarcosmico.framesort.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

/** Marks an item FrameSort hands out (the activator, a pad item) so it can tell it from any item of that material. */
public final class MarkerCodec {

    private MarkerCodec() {
    }

    public static ItemStack mark(ItemStack item, NamespacedKey marker, String value) {
        item.editPersistentDataContainer(pdc -> pdc.set(marker, PersistentDataType.STRING, value));
        return item;
    }

    public static @Nullable String read(ItemStack item, NamespacedKey marker) {
        if (item.isEmpty()) {
            return null;
        }
        return item.getPersistentDataContainer().get(marker, PersistentDataType.STRING);
    }
}
