package com.cesarcosmico.framesort.item;

import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class PlayerCodec {

    private PlayerCodec() {
    }

    public static boolean automark(Player player) {
        return player.getPersistentDataContainer().has(Keys.AUTOMARK);
    }

    public static void setAutomark(Player player, boolean on) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        if (on) {
            data.set(Keys.AUTOMARK, PersistentDataType.BOOLEAN, true);
        } else {
            data.remove(Keys.AUTOMARK);
        }
    }
}
