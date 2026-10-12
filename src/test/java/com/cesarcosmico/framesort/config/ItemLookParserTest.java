package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.item.ItemTemplate;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemLookParserTest {

    private static final Function<String, @Nullable String> ACCEPT_ALL = input -> null;

    private final List<String> warnings = new ArrayList<>();

    private ConfigReader activator(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("activator:\n" + yaml.indent(2));
        return new ConfigReader(config, "config.yml", warnings::add).sectionOrEmpty("activator");
    }

    private ItemTemplate template(String yaml, Function<String, @Nullable String> problem) throws Exception {
        return ItemLookParser.template(activator(yaml), Material.PLAYER_HEAD, problem);
    }

    @Test
    void anEmptySectionMeansAPlainItem() throws Exception {
        ItemTemplate template = template("material: STONE\n", ACCEPT_ALL);
        assertNull(template.look().name());
        assertEquals(List.of(), template.look().lore());
        assertFalse(template.look().glint());
        assertEquals("minecraft:player_head", ItemTemplate.input(template.material(), template.components()));
        assertEquals(List.of(), warnings);
    }

    @Test
    void theLookKeysAreRead() throws Exception {
        ItemTemplate template = template("name: '<red>Sorter'\nlore: ['', ' <gray>Hi</gray> ', '']\nglint: true\n",
                ACCEPT_ALL);
        assertEquals("<red>Sorter", template.look().name());
        assertEquals(List.of("", " <gray>Hi</gray> ", ""), template.look().lore());
        assertTrue(template.look().glint());
    }

    @Test
    void aBlankNameMeansNone() throws Exception {
        assertNull(template("name: ''\n", ACCEPT_ALL).look().name());
        assertNull(template("name: '  '\n", ACCEPT_ALL).look().name());
    }

    @Test
    void componentsBecomeGiveSyntaxInFileOrder() throws Exception {
        String yaml = """
                components:
                  item_model: 'mypack:sorter'
                  dyed_color: 0xFF0000
                  custom_model_data: {floats: [1, 2.5], flags: [true], strings: ['a']}
                  profile:
                    properties:
                      - {name: textures, value: 'abc'}
                  glider: {}
                  'minecraft:rarity': 'epic'
                  custom_data: {big: 10000000000, list: []}
                """;
        ItemTemplate template = template(yaml, ACCEPT_ALL);
        assertEquals("minecraft:player_head[item_model=\"mypack:sorter\",dyed_color=16711680,"
                + "custom_model_data={\"floats\":[1,2.5d],\"flags\":[true],\"strings\":[\"a\"]},"
                + "profile={\"properties\":[{\"name\":\"textures\",\"value\":\"abc\"}]},glider={},"
                + "minecraft:rarity=\"epic\",custom_data={\"big\":10000000000L,\"list\":[]}]",
                ItemTemplate.input(template.material(), template.components()));
        assertEquals(List.of(), warnings);
    }

    @Test
    void textIsAlwaysQuotedAndEscaped() {
        assertEquals("\"a\\\"b\\\\c\"", ItemLookParser.snbt("a\"b\\c"));
        assertEquals("\"true\"", ItemLookParser.snbt("true"));
        assertEquals("\"1\"", ItemLookParser.snbt("1"));
    }

    @Test
    void decimalsNeverUseAnExponent() {
        assertEquals("10000000000d", ItemLookParser.snbt(1.0E10));
        assertEquals("0.0000015d", ItemLookParser.snbt(1.5E-6));
        assertEquals("false", ItemLookParser.snbt(false));
    }

    @Test
    void aBlankComponentMeansWithoutIt() throws Exception {
        ItemTemplate template = template("components:\n  item_model: ''\n  rarity: 'epic'\n", ACCEPT_ALL);
        assertEquals(Map.of("rarity", "\"epic\""), template.components());
        assertEquals(List.of(), warnings);
    }

    @Test
    void anUnusableValueDropsOnlyItsComponent() throws Exception {
        String yaml = """
                components:
                  rarity: 'epic'
                  empty: [~]
                  nan: .nan
                  date: 2026-10-12
                  huge: 99999999999999999999
                """;
        ItemTemplate template = template(yaml, ACCEPT_ALL);
        assertEquals(Map.of("rarity", "\"epic\""), template.components());
        assertEquals(4, warnings.size());
        assertEquals("config.yml > activator.components.empty: empty value, ignored", warnings.get(0));
        assertTrue(warnings.get(2).startsWith("config.yml > activator.components.date: unsupported value"));
    }

    @Test
    void aRejectedComponentIsDroppedWithItsReason() throws Exception {
        Function<String, @Nullable String> problem =
                input -> input.contains("nope=") ? "Unknown item component 'minecraft:nope'" : null;
        ItemTemplate template = template("components:\n  item_model: 'a:b'\n  nope: 1\n", problem);
        assertEquals(Map.of("item_model", "\"a:b\""), template.components());
        assertEquals(List.of("config.yml > activator.components.nope: Unknown item component 'minecraft:nope', "
                + "ignored"), warnings);
    }

    @Test
    void aValidSetIsCheckedOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        template("components:\n  item_model: 'a:b'\n  rarity: 'epic'\n", input -> {
            calls.incrementAndGet();
            return null;
        });
        assertEquals(1, calls.get());
        template("name: 'x'\n", input -> {
            calls.incrementAndGet();
            return null;
        });
        assertEquals(1, calls.get());
    }

    @Test
    void componentsValidOnlyTogetherAreKept() throws Exception {
        Function<String, @Nullable String> problem =
                input -> input.contains("max_damage") && !input.contains("max_stack_size") ? "stackable" : null;
        ItemTemplate template = template("components:\n  max_stack_size: 1\n  max_damage: 10\n", problem);
        assertEquals(2, template.components().size());
        assertEquals(List.of(), warnings);
    }

    @Test
    void componentsThatClashTogetherAreAllLeftOut() throws Exception {
        Function<String, @Nullable String> problem = input -> input.contains("a=") && input.contains("b=")
                ? "clash" : null;
        ItemTemplate template = template("components:\n  a: 1\n  b: 2\n", problem);
        assertEquals(Map.of(), template.components());
        assertEquals(List.of("config.yml > activator.components: clash, none is used"), warnings);
    }

    @Test
    void componentsMustBeASection() throws Exception {
        ItemTemplate template = template("components: 5\n", ACCEPT_ALL);
        assertEquals(Map.of(), template.components());
        assertEquals(List.of("config.yml > activator.components: expected a section of vanilla components, ignored"),
                warnings);
    }
}
