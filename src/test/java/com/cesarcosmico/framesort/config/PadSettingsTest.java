package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.item.ItemLook;
import com.cesarcosmico.framesort.model.ItemReference;
import com.cesarcosmico.framesort.model.PadMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PadSettingsTest {

    // Material::isBlock needs a running server; stick is the only non-block these tests use.
    private static final Predicate<Material> IS_BLOCK = material -> material != Material.STICK;

    private static PadSettings parse(YamlConfiguration yaml, List<String> warnings) {
        return PadSettings.parse(new ConfigReader(yaml, "pads.yml", warnings::add), IS_BLOCK,
                FrameSortSettingsTest.IS_ITEM);
    }

    @Test
    void bundledPadsParseWithoutWarnings() throws Exception {
        List<String> warnings = new ArrayList<>();
        PadSettings settings = parse(FrameSortSettingsTest.bundled("pads.yml"), warnings);

        assertEquals(List.of(), warnings);
        assertEquals(PadMode.ANYONE, settings.creation());
        assertEquals(10, settings.sweepInterval());
        PadType magma = settings.type("MAGMA");
        assertNotNull(magma);
        assertEquals(new ItemReference.Vanilla(Material.MAGMA_BLOCK), magma.top());
        assertEquals(new ItemReference.Vanilla(Material.GILDED_BLACKSTONE), magma.base());
        assertEquals("<gradient:#F7B733:#FC4A1A>Magma Teleport Pad</gradient>", magma.look().name());
    }

    @Test
    void padItemsTakeNoComponents() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                types:
                  simple:
                    structure: [CRYING_OBSIDIAN, GILDED_BLACKSTONE]
                    item: {name: 'Pad', components: {item_model: 'minecraft:stick'}}
                """);
        List<String> warnings = new ArrayList<>();
        PadType simple = parse(yaml, warnings).type("simple");

        assertNotNull(simple);
        assertEquals("Pad", simple.look().name());
        assertEquals(List.of("pads.yml > types.simple.item.components: a pad item looks like the block it places, "
                + "ignored"), warnings);
    }

    @Test
    void craftEngineBlocksTakeTheLookButNoComponents() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                types:
                  ruby:
                    structure: ['craftengine:default:ruby_pad', GILDED_BLACKSTONE]
                    item: {name: 'Pad', glint: true, components: {item_model: 'minecraft:stick'}}
                  mixed:
                    structure: [CRYING_OBSIDIAN, 'craftengine:default:ruby_base']
                  nameless:
                    structure: ['craftengine:ruby_pad', GILDED_BLACKSTONE]
                """);
        List<String> warnings = new ArrayList<>();
        PadSettings settings = parse(yaml, warnings);

        PadType ruby = settings.type("ruby");
        assertNotNull(ruby);
        assertEquals(new ItemReference.Custom("craftengine:default:ruby_pad"), ruby.top());
        assertEquals(new ItemLook("Pad", List.of(), true), ruby.look());
        PadType mixed = settings.type("mixed");
        assertNotNull(mixed);
        assertEquals(new ItemReference.Custom("craftengine:default:ruby_base"), mixed.base());
        assertEquals(List.of("ruby", "mixed"), List.copyOf(settings.types().keySet()));
        assertEquals(2, warnings.size(), warnings::toString);
        assertEquals("pads.yml > types.ruby.item.components: a CraftEngine item keeps its own model and components, "
                + "ignored", warnings.get(0));
        assertTrue(warnings.get(1).startsWith("pads.yml > types.nameless.structure: 'craftengine:ruby_pad' needs"),
                warnings::toString);
    }

    @Test
    void invalidTypesAreSkipped() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                creation: item
                types:
                  glass:
                    structure: [GLASS, BEDROCK]
                  broken:
                    structure: [NOT_A_BLOCK, STONE]
                  single:
                    structure: [STONE]
                  tall:
                    structure: [STONE, STONE, STONE]
                  stick:
                    structure: [GRASS_BLOCK, STICK]
                  watertop:
                    structure: [WATER, STONE]
                  waterbase:
                    structure: [STONE, WATER]
                  flat: 3
                """);
        List<String> warnings = new ArrayList<>();
        PadSettings settings = parse(yaml, warnings);

        assertEquals(PadMode.ITEM, settings.creation());
        assertEquals(List.of("glass"), List.copyOf(settings.types().keySet()));
        // One warning each: an unusable block skips its type.
        assertEquals(7, warnings.size(), warnings::toString);
    }

    @Test
    void noTypesDisablesPads() {
        List<String> warnings = new ArrayList<>();
        PadSettings settings = parse(new YamlConfiguration(), warnings);

        assertTrue(settings.types().isEmpty());
        assertEquals(1, warnings.size());
    }
}
