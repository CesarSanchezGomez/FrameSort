package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.model.ItemReference;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemReferencesTest {

    private final List<String> problems = new ArrayList<>();

    private ItemReference parse(String text) {
        return ItemReferences.parse(text, true, problems::add);
    }

    @Test
    void vanillaMaterialsWithOrWithoutNamespace() {
        assertEquals(new ItemReference.Vanilla(Material.ENDER_EYE), parse("ENDER_EYE"));
        assertEquals(new ItemReference.Vanilla(Material.ENDER_EYE), parse(" minecraft:ender_eye "));
        assertEquals(List.of(), problems);
    }

    @Test
    void craftEngineKeepsItsIdAsWritten() {
        assertEquals(new ItemReference.Custom("craftengine:default:Topaz_Axe"), parse("CraftEngine:default:Topaz_Axe"));
        assertEquals(List.of(), problems);
    }

    @Test
    void eachBadReferenceSaysWhy() {
        assertNull(parse("not_a_thing"));
        assertNull(parse(""));
        assertNull(parse(":stone"));
        assertNull(parse("craftengine:topaz_axe"));
        assertNull(parse("craftengine:default:"));
        assertNull(parse("nexo:ruby"));
        assertEquals(6, problems.size(), problems::toString);
        assertTrue(problems.get(3).contains("'craftengine:default:topaz_axe'"), problems::toString);
        assertTrue(problems.get(5).contains("FrameSort reads no items from 'nexo'"), problems::toString);
    }

    @Test
    void craftEngineReferenceWithoutCraftEngineSaysSo() {
        assertNull(ItemReferences.parse("craftengine:default:ruby", false, problems::add));
        assertEquals(List.of("'craftengine:default:ruby' needs CraftEngine, which is not installed"), problems);
        assertEquals(new ItemReference.Vanilla(Material.STONE), ItemReferences.parse("stone", false, problems::add));
    }
}
