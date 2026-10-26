package com.cesarcosmico.framesort.config;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagSettingsTest {

    // The game's tags need a running server; these are the only ones the tests use.
    private static final Predicate<NamespacedKey> IS_GAME_TAG =
            key -> key.equals(NamespacedKey.minecraft("axes")) || key.equals(NamespacedKey.minecraft("logs"));

    private static TagSettings parse(YamlConfiguration yaml, List<String> warnings) {
        return TagSettings.parse(new ConfigReader(yaml, "tags.yml", warnings::add), FrameSortSettingsTest.IS_ITEM,
                IS_GAME_TAG, true);
    }

    @Test
    void bundledTagsParseWithoutWarnings() throws Exception {
        List<String> warnings = new ArrayList<>();
        TagSettings settings = parse(FrameSortSettingsTest.bundled("tags.yml"), warnings);

        assertEquals(List.of(), warnings);
        assertEquals(Map.of(), settings.tags());
    }

    @Test
    void eachMemberGoesToItsKind() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                tags:
                  Axes:
                    - '#minecraft:axes'
                    - 'diamond_axe'
                    - 'minecraft:GOLDEN_AXE'
                    - 'CraftEngine:default:Topaz_Axe'
                  'server:gems':
                    - 'emerald'
                """);
        List<String> warnings = new ArrayList<>();
        TagSettings settings = parse(yaml, warnings);

        assertEquals(List.of(), warnings);
        TagSettings.CustomTag axes = settings.tags().get(NamespacedKey.minecraft("axes"));
        assertNotNull(axes);
        assertEquals(List.of(Material.DIAMOND_AXE, Material.GOLDEN_AXE), axes.items());
        assertEquals(List.of(NamespacedKey.minecraft("axes")), axes.gameTags());
        assertEquals(List.of("craftengine:default:Topaz_Axe"), axes.customItems());
        TagSettings.CustomTag gems = settings.tags().get(NamespacedKey.fromString("server:gems"));
        assertNotNull(gems);
        assertEquals(List.of(Material.EMERALD), gems.items());
    }

    @Test
    void craftEngineItemsWithoutCraftEngineAreIgnored() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                tags:
                  axes:
                    - 'diamond_axe'
                    - 'craftengine:default:topaz_axe'
                """);
        List<String> warnings = new ArrayList<>();
        TagSettings settings = TagSettings.parse(new ConfigReader(yaml, "tags.yml", warnings::add),
                FrameSortSettingsTest.IS_ITEM, IS_GAME_TAG, false);

        TagSettings.CustomTag axes = settings.tags().get(NamespacedKey.minecraft("axes"));
        assertNotNull(axes);
        assertEquals(List.of(Material.DIAMOND_AXE), axes.items());
        assertEquals(List.of(), axes.customItems());
        assertEquals(List.of("tags.yml > tags.axes: 'craftengine:default:topaz_axe' needs CraftEngine, which is not "
                + "installed; ignored"), warnings);
    }

    @Test
    void eachBadEntryWarnsOnceAndIsIgnored() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                tags:
                  'Bad Name!': ['diamond']
                  logs: ['#minecraft:logs']
                  'minecraft:logs': ['oak_log']
                  mixed:
                    - 'not_an_item'
                    - 'water'
                    - '#minecraft:nope'
                    - '#Bad Tag'
                    - 'nexo:ruby'
                    - 'craftengine:topaz_axe'
                    - ''
                    - ':stone'
                    - 'stone'
                  empty: []
                """);
        List<String> warnings = new ArrayList<>();
        TagSettings settings = parse(yaml, warnings);

        assertEquals(Set.of(NamespacedKey.minecraft("logs"), NamespacedKey.minecraft("mixed")),
                settings.tags().keySet());
        // The first of two names for one tag counts.
        assertEquals(List.of(NamespacedKey.minecraft("logs")),
                settings.tags().get(NamespacedKey.minecraft("logs")).gameTags());
        assertEquals(List.of(Material.STONE), settings.tags().get(NamespacedKey.minecraft("mixed")).items());
        // The bad name, the repeated name, eight bad members and the tag left empty.
        assertEquals(11, warnings.size(), warnings::toString);
        assertTrue(warnings.stream().allMatch(w -> w.startsWith("tags.yml > tags.")), warnings::toString);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("'craftengine:default:topaz_axe'")), warnings::toString);
    }

    @Test
    void noTagsSectionMeansNoTags() {
        List<String> warnings = new ArrayList<>();

        assertEquals(Map.of(), parse(new YamlConfiguration(), warnings).tags());
        assertEquals(List.of(), warnings);
    }
}
