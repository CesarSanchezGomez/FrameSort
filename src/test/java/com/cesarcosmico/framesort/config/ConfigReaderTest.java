package com.cesarcosmico.framesort.config;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigReaderTest {

    private final List<String> warnings = new ArrayList<>();

    private ConfigReader reader(String text) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return new ConfigReader(yaml, "config.yml", warnings::add);
    }

    @Test
    void missingValuesUseTheDefaultSilently() throws Exception {
        ConfigReader reader = reader("");
        assertEquals("x", reader.string("a", "x"));
        assertEquals(5, reader.integer("b", 5, 0, 10));
        assertEquals(0.5, reader.decimal("c", 0.5, 0, 1));
        assertTrue(reader.bool("d", true));
        assertEquals(List.of(), warnings);
    }

    @Test
    void invalidValuesWarnWithFileAndPathAndUseTheDefault() throws Exception {
        ConfigReader reader = reader("limits:\n  radius: 'far'\n");
        assertEquals(64, reader.sectionOrEmpty("limits").integer("radius", 64, 1, 128));
        assertEquals(List.of("config.yml > limits.radius: expected a whole number, using 64"), warnings);
    }

    @Test
    void outOfRangeNumbersWarn() throws Exception {
        ConfigReader reader = reader("radius: 500\nchance: 1.5\n");
        assertEquals(64, reader.integer("radius", 64, 1, 128));
        assertEquals(0.5, reader.decimal("chance", 0.5, 0, 1));
        assertEquals(2, warnings.size());
    }

    @Test
    void decimalsAcceptWholeNumbers() throws Exception {
        assertEquals(2.0, reader("price: 2\n").decimal("price", 1, 0, 10));
    }

    @Test
    void booleansMustBeBooleans() throws Exception {
        ConfigReader reader = reader("enabled: 'yes please'\n");
        assertTrue(reader.bool("enabled", true));
        assertEquals(1, warnings.size());
    }

    @Test
    void materialsAcceptNamespacedNamesAndBlankMeansNone() throws Exception {
        ConfigReader reader = reader(
                "tool: 'minecraft:stick'\nnone: ''\nbad: 'not_a_block'\nlist: ['stone', 'nope']\n");
        assertEquals(Material.STICK, reader.material("tool", null));
        assertNull(reader.material("none", Material.STONE));
        assertEquals(Material.STONE, reader.requiredMaterial("bad", Material.STONE));
        assertEquals(List.of(Material.STONE), reader.materials("list"));
        assertEquals(2, warnings.size());
    }

    @Test
    void coloursAreHexWithOrWithoutHash() throws Exception {
        ConfigReader reader = reader("a: '#FF0000'\nb: '00ff00'\nc: 'red'\n");
        assertEquals(Color.fromRGB(0xFF0000), reader.color("a", Color.WHITE));
        assertEquals(Color.fromRGB(0x00FF00), reader.color("b", Color.WHITE));
        assertEquals(Color.WHITE, reader.color("c", Color.WHITE));
        assertEquals(List.of("config.yml > c: 'red' is not a #RRGGBB colour, using #FFFFFF"), warnings);
    }

    @Test
    void keysAreLowerCasedAndInvalidOnesIgnored() throws Exception {
        ConfigReader reader = reader("model: 'MyPack:Sword'\nbad: 'a b'\n");
        assertEquals(NamespacedKey.fromString("mypack:sword"), reader.key("model"));
        assertNull(reader.key("bad"));
        assertEquals(1, warnings.size());
    }

    @Test
    void enumsAcceptKebabCase() throws Exception {
        ConfigReader reader = reader("day: 'saturday'\ndays: ['monday', 'funday']\nbad: 'someday'\n");
        assertEquals(DayOfWeek.SATURDAY, reader.enumValue("day", DayOfWeek.class, DayOfWeek.SUNDAY));
        assertEquals(Set.of(DayOfWeek.MONDAY), reader.enumSet("days", DayOfWeek.class, Set.of(DayOfWeek.SUNDAY)));
        assertEquals(DayOfWeek.SUNDAY, reader.enumValue("bad", DayOfWeek.class, DayOfWeek.SUNDAY));
        assertEquals(2, warnings.size());
    }

    @Test
    void anEnumSetWithNoValidValueFallsBack() throws Exception {
        assertEquals(Set.of(DayOfWeek.SUNDAY),
                reader("days: ['x']\n").enumSet("days", DayOfWeek.class, Set.of(DayOfWeek.SUNDAY)));
    }
}
