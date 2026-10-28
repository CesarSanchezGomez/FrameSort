package com.cesarcosmico.framesort.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

public final class MarkerCodec {

    private MarkerCodec() {
    }

    public static ItemStack mark(ItemStack item, NamespacedKey marker, String value) {
        item.editPersistentDataContainer(pdc -> pdc.set(marker, PersistentDataType.STRING, value));
        return item;
    }

    public static @Nullable String read(ItemStack item, NamespacedKey marker) {
        return item.getPersistentDataContainer().get(marker, PersistentDataType.STRING);
    }
}
