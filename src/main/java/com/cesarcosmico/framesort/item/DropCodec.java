package com.cesarcosmico.framesort.item;

import org.bukkit.entity.Item;
import org.bukkit.persistence.PersistentDataType;

/**
 * Marks the items FrameSort drops at a drop spot, so no pad pulls them back. The mark is on the item entity, not on
 * its stack: it goes away once someone picks the item up.
 */
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
