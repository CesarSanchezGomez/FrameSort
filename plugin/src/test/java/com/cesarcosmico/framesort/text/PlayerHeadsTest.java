package com.cesarcosmico.framesort.text;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlayerHeadsTest {

    private static final UUID MOJANG = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID OFFLINE = UUID.nameUUIDFromBytes("OfflinePlayer:Pepe".getBytes(StandardCharsets.UTF_8));
    private static final UUID FLOODGATE = new UUID(0, 2535428567390000L);
    private static final Key STEVE = Key.key("entity/player/wide/steve");

    private static @Nullable PlayerHeadObjectContents head(UUID player, UUID viewer) {
        return find(MiniMessage.miniMessage().deserialize("<player-head>", PlayerHeads.head(player, viewer)));
    }

    private static @Nullable PlayerHeadObjectContents find(Component component) {
        if (component instanceof ObjectComponent object
                && object.contents() instanceof PlayerHeadObjectContents contents) {
            return contents;
        }
        for (Component child : component.children()) {
            PlayerHeadObjectContents found = find(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    void aMojangAccountGetsItsOwnHeadByUuidOnly() {
        PlayerHeadObjectContents head = head(MOJANG, MOJANG);
        assertNotNull(head);
        assertEquals(MOJANG, head.id());
        assertNull(head.name());
        assertNull(head.texture());
    }

    @Test
    void anOfflineModeAccountGetsSteve() {
        PlayerHeadObjectContents head = head(OFFLINE, MOJANG);
        assertNotNull(head);
        assertEquals(STEVE, head.texture());
        assertNull(head.id());
    }

    @Test
    void aBedrockAccountGetsSteve() {
        PlayerHeadObjectContents head = head(FLOODGATE, MOJANG);
        assertNotNull(head);
        assertEquals(STEVE, head.texture());
        assertNull(head.id());
    }

    @Test
    void aBedrockViewerGetsNoHead() {
        assertNull(head(MOJANG, FLOODGATE));
    }
}
