package com.cesarcosmico.framesort.item;

import org.bukkit.entity.Item;
import org.bukkit.persistence.PersistentDataType;

public final class DropCodec {

    private DropCodec() {
    }

    public static void mark(Item item) {
        item.getPersistentDataContainer().set(Keys.DROPPED, PersistentDataType.BOOLEAN, true);
    }

    public static boolean isMarked(Item item) {
        return item.getPersistentDataContainer().has(Keys.DROPPED);
    }
}
