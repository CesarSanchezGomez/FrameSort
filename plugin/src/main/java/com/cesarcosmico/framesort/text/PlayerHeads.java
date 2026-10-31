package com.cesarcosmico.framesort.text;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.object.ObjectContents;

import java.util.UUID;

public final class PlayerHeads {

    private static final int OFFLINE_UUID_VERSION = 3;
    private static final Component STEVE = Component.object(
            ObjectContents.playerHead().texture(Key.key("entity/player/wide/steve")).build());

    private PlayerHeads() {
    }

    /**
     * {@code <player-head>}: the head of {@code player}, Steve's when Mojang has no skin for that UUID, or nothing when
     * {@code viewer} plays on Bedrock.
     */
    public static TagResolver head(UUID player, UUID viewer) {
        return Placeholder.component("player-head", headFor(player, viewer));
    }

    private static Component headFor(UUID player, UUID viewer) {
        // Geyser turns a head into the text "[unknown player head]".
        if (isFloodgateUuid(viewer)) {
            return Component.empty();
        }
        if (isOfflineUuid(player) || isFloodgateUuid(player)) {
            return STEVE;
        }
        // Only the UUID: the client looks the skin up.
        return Component.object(ObjectContents.playerHead(player));
    }

    private static boolean isOfflineUuid(UUID uuid) {
        return uuid.version() == OFFLINE_UUID_VERSION;
    }

    private static boolean isFloodgateUuid(UUID uuid) {
        return uuid.getMostSignificantBits() == 0;
    }
}
