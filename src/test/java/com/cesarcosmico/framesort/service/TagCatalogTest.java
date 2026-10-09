package com.cesarcosmico.framesort.service;

import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TagCatalogTest {

    private static final NamespacedKey LOGS = NamespacedKey.minecraft("logs");
    private static final NamespacedKey DRY_VEGETATION =
            NamespacedKey.minecraft("triggers_ambient_desert_dry_vegetation_block_sounds");
    // What the anvil leaves of the name: its first 50 characters.
    private static final String CUT = ("#" + DRY_VEGETATION.getKey()).substring(0, 50);

    @Test
    void aNameIsATagOnlyWithALeadingHash() {
        assertEquals(LOGS, TagCatalog.parseName("#logs"));
        assertEquals(LOGS, TagCatalog.parseName("#minecraft:logs"));
        assertEquals(LOGS, TagCatalog.parseName("  #LOGS "));
        assertNull(TagCatalog.parseName("logs"));
        assertNull(TagCatalog.parseName("minecraft:logs"));
        assertNull(TagCatalog.parseName("#"));
        assertNull(TagCatalog.parseName("# logs"));
    }

    @Test
    void aNameCutByTheAnvilCompletesToTheOnlyTagItStartsLike() {
        assertEquals(DRY_VEGETATION, TagCatalog.complete(CUT, List.of(LOGS, DRY_VEGETATION)));
    }

    @Test
    void anAmbiguousCutNameCompletesToNothing() {
        NamespacedKey other = NamespacedKey.minecraft(DRY_VEGETATION.getKey() + "_too");
        assertNull(TagCatalog.complete(CUT, List.of(DRY_VEGETATION, other)));
    }

    @Test
    void shorterNamesAreNeverCompleted() {
        assertNull(TagCatalog.complete("#log", List.of(LOGS)));
    }
}
