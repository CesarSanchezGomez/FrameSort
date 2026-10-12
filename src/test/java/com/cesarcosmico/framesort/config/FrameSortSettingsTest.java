package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.model.TargetRegistration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameSortSettingsTest {

    // Material::isItem needs a running server; water is the only non-item these tests use.
    static final Predicate<Material> IS_ITEM = material -> material != Material.WATER;

    static YamlConfiguration bundled(String resource) throws Exception {
        try (InputStream in = FrameSortSettingsTest.class.getResourceAsStream("/" + resource)) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static FrameSortSettings parse(YamlConfiguration yaml, List<String> warnings) {
        return FrameSortSettings.parse(new ConfigReader(yaml, "config.yml", warnings::add), IS_ITEM);
    }

    @Test
    void bundledConfigParsesWithoutWarnings() throws Exception {
        List<String> warnings = new ArrayList<>();
        FrameSortSettings settings = parse(bundled("config.yml"), warnings);

        assertEquals(List.of(), warnings);
        assertEquals("en_US", settings.language());
        assertEquals(Material.ENDER_EYE, settings.sorter().activator().material());
        assertFalse(settings.sorter().requireMarked());
        assertEquals(20, settings.sorter().tickRate());
        assertTrue(settings.sorter().showActivity());
        assertEquals(TargetRegistration.MANUAL, settings.targets().registration());
        assertEquals(64, settings.delivery().maxDistance());
        assertTrue(settings.delivery().insertIntoContainers());
        assertEquals(Material.CARROT_ON_A_STICK, settings.delivery().defaultTargetItem());
        assertNull(settings.sorter().activator().itemModel());
        assertEquals(5, settings.inspect().highlightSeconds());
        assertEquals(Color.fromRGB(0x55FF55), settings.inspect().colors().container());
        assertEquals(Color.fromRGB(0xFF5555), settings.inspect().colors().lava());
    }

    @Test
    void emptyConfigUsesDefaults() {
        List<String> warnings = new ArrayList<>();
        FrameSortSettings settings = parse(new YamlConfiguration(), warnings);

        assertEquals(List.of(), warnings);
        assertEquals(64, settings.delivery().maxDistance());
        assertEquals(Set.of(EntityType.ITEM_FRAME, EntityType.GLOW_ITEM_FRAME), settings.sorter().frameTypes());
        assertEquals(8, settings.inspect().pageSize());
        assertEquals("<gradient:#F7B733:#FC4A1A>#<tag></gradient>", settings.targets().taggedName());
        assertTrue(settings.targets().taggedGlint());
    }

    @Test
    void invalidValuesFallBackWithAWarningEach() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                language: ""
                sorter:
                  activator: { material: NOT_A_THING }
                  tick-rate: 0
                  frame-types: [ITEM_FRAME, ZOMBIE]
                targets:
                  registration: sometimes
                delivery:
                  max-distance: lots
                  default-target-item: ""
                inspect:
                  colors: { dropped: "#ABC", lava: "#00ff7f" }
                """);
        List<String> warnings = new ArrayList<>();
        FrameSortSettings settings = parse(yaml, warnings);

        assertEquals("en_US", settings.language());
        assertEquals(Material.ENDER_EYE, settings.sorter().activator().material());
        assertEquals(20, settings.sorter().tickRate());
        assertEquals(Set.of(EntityType.ITEM_FRAME), settings.sorter().frameTypes());
        assertEquals(TargetRegistration.MANUAL, settings.targets().registration());
        assertEquals(64, settings.delivery().maxDistance());
        assertNull(settings.delivery().defaultTargetItem());
        assertEquals(Color.fromRGB(0xFFFF55), settings.inspect().colors().dropped());
        assertEquals(Color.fromRGB(0x00FF7F), settings.inspect().colors().lava());
        // default-target-item "" is a valid "off", so it does not warn.
        assertEquals(7, warnings.size(), warnings::toString);
        assertTrue(warnings.stream().allMatch(w -> w.startsWith("config.yml > ")), warnings::toString);
    }

    @Test
    void aTraceDefaultAboveTheMaximumIsLoweredWithAWarning() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                inspect:
                  trace-max-seconds: 30
                  trace-default-seconds: 60
                """);
        List<String> warnings = new ArrayList<>();
        FrameSortSettings settings = parse(yaml, warnings);

        assertEquals(30, settings.inspect().traceDefaultSeconds());
        assertEquals(30, settings.inspect().traceMaxSeconds());
        assertEquals(1, warnings.size(), warnings::toString);
    }

    @Test
    void leavingOutTheOptionalKeysMeansWithoutThem() throws Exception {
        YamlConfiguration bundled = bundled("config.yml");
        YamlConfiguration live = bundled("config.yml");
        for (String key : FrameSortSettings.OPTIONAL_KEYS) {
            // Each one stays in the bundled file, so the admin sees it and a typo of it is still reported.
            assertTrue(bundled.isSet(key), key);
            live.set(key, null);
        }
        List<String> warnings = new ArrayList<>();
        ConfigValidator.compare(live, bundled, "config.yml", Set.of(), FrameSortSettings.OPTIONAL_KEYS, warnings::add);
        SorterSettings sorter = parse(live, warnings).sorter();

        assertEquals(List.of(), warnings);
        assertNull(sorter.activator().name());
        assertEquals(List.of(), sorter.activator().lore());
        assertFalse(sorter.activator().glint());
        assertNull(sorter.activator().itemModel());
        assertNull(sorter.customName());
    }

    @Test
    void materialsThatAreNotItemsFallBack() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                sorter:
                  activator: { material: WATER }
                delivery:
                  default-target-item: WATER
                """);
        List<String> warnings = new ArrayList<>();
        FrameSortSettings settings = parse(yaml, warnings);

        assertEquals(Material.ENDER_EYE, settings.sorter().activator().material());
        assertEquals(Material.CARROT_ON_A_STICK, settings.delivery().defaultTargetItem());
        assertEquals(2, warnings.size(), warnings::toString);
    }
}
