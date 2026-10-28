package com.cesarcosmico.framesort.service;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public final class Players {

    private Players() {
    }

    // Player inherits two getLocation(): Entity's, never null, and OfflinePlayer's, null for a player the server never
    // saw. A call on a Player binds to OfflinePlayer's, so it reads as nullable; a connected player is an entity.
    public static Location location(Player player) {
        return ((Entity) player).getLocation();
    }
}
