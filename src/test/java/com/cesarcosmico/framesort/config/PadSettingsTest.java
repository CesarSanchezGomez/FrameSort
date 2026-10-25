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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PadSettingsTest {

    // Material::isBlock needs a running server; stick is the only non-block these tests use.
    private static final Predicate<Material> IS_BLOCK = material -> material != Material.STICK;

    private static PadSettings parse(YamlConfiguration yaml, List<String> warnings) {
        return PadSettings.parse(new ConfigReader(yaml, "pads.yml", warnings::add), IS_BLOCK,
                FrameSortSettingsTest.IS_ITEM, true);
    }

    @Test
    void bundledPadsParseWithoutWarnings() throws Exception {
        List<String> warnings = new ArrayList<>();
        PadSettings settings = parse(FrameSortSettingsTest.bundled("pads.yml"), warnings);

        assertEquals(List.of(), warnings);
        assertEquals(PadMode.ANYONE, settings.creation());
        assertEquals(10, settings.sweepInterval());
        PadType copper = settings.type("COPPER");
        assertNotNull(copper);
        assertEquals(new ItemReference.Vanilla(Material.WAXED_COPPER_GRATE), copper.top());
        assertEquals(new ItemReference.Vanilla(Material.WAXED_COPPER_BLOCK), copper.base());
        assertEquals(64, copper.range());
        assertEquals(4, copper.pullRadius());
        assertFalse(copper.takesFromInventory());
        assertEquals("<gradient:#F7B733:#FC4A1A>Copper Sorting Pad</gradient>", copper.look().name());
        PadType lodestone = settings.type("lodestone");
        assertNotNull(lodestone);
        assertEquals(new ItemReference.Vanilla(Material.LODESTONE), lodestone.top());
        assertEquals(new ItemReference.Vanilla(Material.SMOOTH_STONE), lodestone.base());
        PadType tuff = settings.type("tuff");
        assertNotNull(tuff);
        assertEquals(new ItemReference.Vanilla(Material.CHISELED_TUFF), tuff.top());
        assertEquals(new ItemReference.Vanilla(Material.POLISHED_TUFF), tuff.base());
        assertEquals(0, tuff.pullRadius());
        assertTrue(tuff.takesFromInventory());
    }

    @Test
    void eachTypeHasItsOwnRangePullRadiusAndInventoryMode() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                types:
                  near:
                    structure: [CRYING_OBSIDIAN, GILDED_BLACKSTONE]
                    range: 8
                    pull-radius: 0
                    takes-from-inventory: true
                  far:
                    structure: [GLASS, BEDROCK]
                    range: 200
                    pull-radius: 16
                    takes-from-inventory: false
                """);
        List<String> warnings = new ArrayList<>();
        PadSettings settings = parse(yaml, warnings);

        assertEquals(List.of(), warnings);
        PadType near = settings.type("near");
        assertNotNull(near);
        assertEquals(8, near.range());
        assertEquals(0, near.pullRadius());
        assertTrue(near.takesFromInventory());
        PadType far = settings.type("far");
        assertNotNull(far);
        assertEquals(200, far.range());
        assertEquals(16, far.pullRadius());
        assertFalse(far.takesFromInventory());
    }

    @Test
    void aMissingOrWrongTypeSettingWarnsAndUsesTheDefault() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                types:
                  missing:
                    structure: [CRYING_OBSIDIAN, GILDED_BLACKSTONE]
                  wrong:
                    structure: [GLASS, BEDROCK]
                    range: 0
                    pull-radius: 40
                    takes-from-inventory: 3
                """);
        List<String> warnings = new ArrayList<>();
        PadSettings settings = parse(yaml, warnings);

        for (String id : List.of("missing", "wrong")) {
            PadType type = settings.type(id);
            assertNotNull(type);
            assertEquals(64, type.range());
            assertEquals(4, type.pullRadius());
            assertFalse(type.takesFromInventory());
        }
        assertEquals(List.of("pads.yml > types.missing.range: missing, using 64",
                "pads.yml > types.missing.pull-radius: missing, using 4",
                "pads.yml > types.missing.takes-from-inventory: missing, using false",
                "pads.yml > types.wrong.range: 0 is outside 1..512, using 64",
                "pads.yml > types.wrong.pull-radius: 40 is outside 0..16, using 4",
                "pads.yml > types.wrong.takes-from-inventory: expected true or false, using false"), warnings);
    }

    @Test
    void unknownKeysOfATypeOrItsItemAreReported() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                types:
                  simple:
                    structure: [CRYING_OBSIDIAN, GILDED_BLACKSTONE]
                    range: 64
                    pull-radius: 4
                    pul-radius: 8
                    takes-from-inventory: false
                    item: {name: 'Pad', lroe: ['x']}
                """);
        List<String> warnings = new ArrayList<>();
        PadType simple = parse(yaml, warnings).type("simple");

        assertNotNull(simple);
        assertEquals(4, simple.pullRadius());
        assertEquals(List.of("pads.yml > types.simple.pul-radius: unknown key, typo or removed setting; ignored",
                "pads.yml > types.simple.item.lroe: unknown key, typo or removed setting; ignored"), warnings);
    }

    @Test
    void aTypeIdWithACommaIsSkipped() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                types:
                  'a,b':
                    structure: [CRYING_OBSIDIAN, GILDED_BLACKSTONE]
                  simple:
                    structure: [CRYING_OBSIDIAN, GILDED_BLACKSTONE]
                    range: 64
                    pull-radius: 4
                    takes-from-inventory: false
                """);
        List<String> warnings = new ArrayList<>();
        PadSettings settings = parse(yaml, warnings);

        assertEquals(1, settings.types().size());
        assertNotNull(settings.type("simple"));
        assertEquals(List.of("pads.yml > types.a,b: a pad type id can't contain ','; pad type 'a,b' is skipped"),
                warnings);
    }

    @Test
    void padItemsTakeNoComponents() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                types:
                  simple:
                    structure: [CRYING_OBSIDIAN, GILDED_BLACKSTONE]
                    range: 64
                    pull-radius: 4
                    takes-from-inventory: false
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
                    range: 64
                    pull-radius: 4
                    takes-from-inventory: false
                    item: {name: 'Pad', glint: true, components: {item_model: 'minecraft:stick'}}
                  mixed:
                    structure: [CRYING_OBSIDIAN, 'craftengine:default:ruby_base']
                    range: 64
                    pull-radius: 4
                    takes-from-inventory: false
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
    void craftEngineBlocksWithoutCraftEngineSkipTheirType() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                types:
                  ruby:
                    structure: ['craftengine:default:ruby_pad', GILDED_BLACKSTONE]
                  plain:
                    structure: [CRYING_OBSIDIAN, GILDED_BLACKSTONE]
                    range: 64
                    pull-radius: 4
                    takes-from-inventory: false
                """);
        List<String> warnings = new ArrayList<>();
        PadSettings settings = PadSettings.parse(new ConfigReader(yaml, "pads.yml", warnings::add), IS_BLOCK,
                FrameSortSettingsTest.IS_ITEM, false);

        assertEquals(List.of("plain"), List.copyOf(settings.types().keySet()));
        assertEquals(List.of("pads.yml > types.ruby.structure: 'craftengine:default:ruby_pad' needs CraftEngine, "
                + "which is not installed; pad type 'ruby' is skipped"), warnings);
    }

    @Test
    void invalidTypesAreSkipped() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                creation: item
                types:
                  glass:
                    structure: [GLASS, BEDROCK]
                    range: 64
                    pull-radius: 4
                    takes-from-inventory: false
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
