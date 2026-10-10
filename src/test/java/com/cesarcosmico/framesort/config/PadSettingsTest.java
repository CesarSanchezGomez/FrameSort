package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.model.PadMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertEquals(Material.MAGMA_BLOCK, magma.top());
        assertEquals(Material.GILDED_BLACKSTONE, magma.base());
        assertEquals(Material.MAGMA_BLOCK, magma.item().material());
    }

    @Test
    void padItemsKeepTheBlockModel() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                types:
                  simple:
                    structure: [CRYING_OBSIDIAN, GILDED_BLACKSTONE]
                    item: {item-model: 'minecraft:stick'}
                """);
        PadType simple = parse(yaml, new ArrayList<>()).type("simple");

        assertNotNull(simple);
        assertNull(simple.item().itemModel());
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
        // broken warns twice: the unknown material, then a structure left with one block.
        assertEquals(8, warnings.size(), warnings::toString);
    }

    @Test
    void noTypesDisablesPads() {
        List<String> warnings = new ArrayList<>();
        PadSettings settings = parse(new YamlConfiguration(), warnings);

        assertTrue(settings.types().isEmpty());
        assertEquals(1, warnings.size());
    }
}
