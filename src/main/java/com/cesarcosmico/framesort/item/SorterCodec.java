package com.cesarcosmico.framesort.item;

import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** A sorter keeps its owner and its count in its container's own data, so both travel with the block. */
public final class SorterCodec {

    private SorterCodec() {
    }

    /** The sorter's owner, or {@code null} when the container is no sorter; an unreadable value counts as none. */
    public static @Nullable UUID owner(TileState container) {
        return OwnerText.parse(container.getPersistentDataContainer().get(Keys.SORTER, PersistentDataType.STRING));
    }

    public static void mark(TileState container, UUID owner) {
        container.getPersistentDataContainer().set(Keys.SORTER, PersistentDataType.STRING, owner.toString());
    }

    /** How many items the sorter has sent. */
    public static long sorted(TileState container) {
        return container.getPersistentDataContainer().getOrDefault(Keys.SORTED, PersistentDataType.LONG, 0L);
    }

    public static void addSorted(TileState container, long amount) {
        container.getPersistentDataContainer().set(Keys.SORTED, PersistentDataType.LONG, sorted(container) + amount);
    }
}
