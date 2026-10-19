package com.cesarcosmico.framesort.item;

import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** A player's own choices, saved with the player on this server and only read while they play. */
public final class PlayerCodec {

    private PlayerCodec() {
    }

    public static boolean autoMark(Player player) {
        return player.getPersistentDataContainer().has(Keys.AUTO_MARK);
    }

    public static void setAutoMark(Player player, boolean on) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        if (on) {
            data.set(Keys.AUTO_MARK, PersistentDataType.BOOLEAN, true);
        } else {
            data.remove(Keys.AUTO_MARK);
        }
    }
}
