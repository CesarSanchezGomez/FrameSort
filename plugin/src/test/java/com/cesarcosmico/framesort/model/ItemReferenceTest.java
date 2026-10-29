package com.cesarcosmico.framesort.model;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemReferenceTest {

    private static final ItemReference EYE = new ItemReference.Vanilla(Material.ENDER_EYE);
    private static final ItemReference RUBY = new ItemReference.Custom("craftengine:default:ruby");

    @Test
    void aVanillaReferenceMatchesItsMaterialButNoCustomItemMadeFromIt() {
        assertTrue(EYE.matches(Material.ENDER_EYE, null));
        assertFalse(EYE.matches(Material.ENDER_EYE, "craftengine:default:ruby"));
        assertFalse(EYE.matches(Material.STONE, null));
    }

    @Test
    void aCustomReferenceMatchesOnlyThatCustomItem() {
        assertTrue(RUBY.matches(Material.ENDER_EYE, "craftengine:default:ruby"));
        assertTrue(RUBY.matches(Material.PAPER, "craftengine:default:ruby"));
        assertFalse(RUBY.matches(Material.PAPER, "craftengine:default:Ruby"));
        assertFalse(RUBY.matches(Material.PAPER, null));
    }
}
