package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.model.FramePosition;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameSortSettingsTest {

    static YamlConfiguration bundled(String resource) throws Exception {
        try (InputStream in = FrameSortSettingsTest.class.getResourceAsStream("/" + resource)) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static FrameSortSettings parse(YamlConfiguration yaml, List<String> warnings) {
        return FrameSortSettings.parse(new ConfigReader(yaml, "config.yml", warnings::add));
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
        assertEquals(Set.of(FramePosition.FRONT), settings.targets().positions());
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
        assertEquals(Set.of(FramePosition.FRONT), settings.targets().positions());
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
                  positions: [FRONT, SIDES, UNDER]
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
        assertEquals(Set.of(FramePosition.FRONT, FramePosition.LEFT, FramePosition.RIGHT),
                settings.targets().positions());
        assertEquals(64, settings.delivery().maxDistance());
        assertNull(settings.delivery().defaultTargetItem());
        assertEquals(Color.fromRGB(0xFFFF55), settings.inspect().colors().dropped());
        assertEquals(Color.fromRGB(0x00FF7F), settings.inspect().colors().lava());
        // default-target-item "" is a valid "off", so it does not warn.
        assertEquals(8, warnings.size(), warnings::toString);
        assertTrue(warnings.stream().allMatch(w -> w.startsWith("config.yml > ")), warnings::toString);
    }
}
