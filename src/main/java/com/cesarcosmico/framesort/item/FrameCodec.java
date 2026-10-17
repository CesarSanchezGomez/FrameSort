package com.cesarcosmico.framesort.item;

import org.bukkit.entity.ItemFrame;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public final class FrameCodec {

    private FrameCodec() {
    }

    public static boolean isMarked(ItemFrame frame) {
        return frame.getPersistentDataContainer().has(Keys.TARGET);
    }

    public static void setMarked(ItemFrame frame, boolean marked) {
        PersistentDataContainer data = frame.getPersistentDataContainer();
        if (marked) {
            data.set(Keys.TARGET, PersistentDataType.BOOLEAN, true);
        } else {
            data.remove(Keys.TARGET);
        }
    }

    /** The owner, or {@code null} for a frame nobody owns; an unreadable value counts as nobody. */
    public static @Nullable UUID owner(ItemFrame frame) {
        return OwnerText.parse(frame.getPersistentDataContainer().get(Keys.OWNER, PersistentDataType.STRING));
    }

    public static void setOwner(ItemFrame frame, UUID owner) {
        frame.getPersistentDataContainer().set(Keys.OWNER, PersistentDataType.STRING, owner.toString());
    }
}
