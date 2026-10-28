package com.cesarcosmico.framesort.item;

import org.bukkit.block.Dispenser;
import org.bukkit.persistence.PersistentDataType;

public final class SorterCodec {

    private SorterCodec() {
    }

    public static long sorted(Dispenser dispenser) {
        return dispenser.getPersistentDataContainer().getOrDefault(Keys.SORTED, PersistentDataType.LONG, 0L);
    }

    public static void addSorted(Dispenser dispenser, long sent) {
        dispenser.getPersistentDataContainer().set(Keys.SORTED, PersistentDataType.LONG, sorted(dispenser) + sent);
    }
}
