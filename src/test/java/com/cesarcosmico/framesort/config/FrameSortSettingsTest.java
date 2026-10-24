package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.item.ItemLook;
import com.cesarcosmico.framesort.model.ItemReference;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameSortSettingsTest {

    // Material::isItem needs a running server; water is the only non-item these tests use.
    static final Predicate<Material> IS_ITEM = material -> material != Material.WATER;
    // Minecraft checks components on a server; these tests reject only the component named "nope".
    static final Function<String, @Nullable String> PROBLEM =
            input -> input.contains("nope=") ? "Unknown item component 'minecraft:nope'" : null;
    private static final ItemReference HOPPER = new ItemReference.Vanilla(Material.HOPPER);
    private static final ItemReference FRAME = new ItemReference.Vanilla(Material.ITEM_FRAME);

    static YamlConfiguration bundled(String resource) throws Exception {
        try (InputStream in = FrameSortSettingsTest.class.getResourceAsStream("/" + resource)) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static FrameSortSettings parse(YamlConfiguration yaml, List<String> warnings) {
        return FrameSortSettings.parse(new ConfigReader(yaml, "config.yml", warnings::add), IS_ITEM, PROBLEM, true);
    }

    @Test
    void bundledConfigParsesWithoutWarnings() throws Exception {
        List<String> warnings = new ArrayList<>();
        FrameSortSettings settings = parse(bundled("config.yml"), warnings);

        assertEquals(List.of(), warnings);
        assertEquals("en_US", settings.language());
        assertEquals(HOPPER, settings.sorter().activator());
        assertFalse(settings.sorter().requireMarked());
        assertEquals(20, settings.sorter().tickRate());
        assertTrue(settings.sorter().showActivity());
        assertEquals(64, settings.sorter().range());
        assertTrue(settings.delivery().insertIntoContainers());
        assertEquals(FRAME, settings.delivery().defaultTargetItem());
        assertEquals(Map.of(), settings.sorter().activatorComponents());
        assertEquals("<gradient:#F7B733:#FC4A1A>#<tag></gradient>", settings.targets().taggedItem().name());
        assertTrue(settings.targets().taggedItem().glint());
        assertEquals(5, settings.inspect().highlightSeconds());
        assertEquals(Color.fromRGB(0x55FF55), settings.inspect().colors().container());
        assertEquals(Color.fromRGB(0xFF5555), settings.inspect().colors().lava());
    }

    @Test
    void emptyConfigUsesDefaults() {
        List<String> warnings = new ArrayList<>();
        FrameSortSettings settings = parse(new YamlConfiguration(), warnings);

        assertEquals(List.of(), warnings);
        assertEquals(64, settings.sorter().range());
        assertEquals(Set.of(EntityType.ITEM_FRAME, EntityType.GLOW_ITEM_FRAME), settings.sorter().frameTypes());
        assertEquals(8, settings.inspect().pageSize());
        // Without the section a tag changes nothing in the item's look.
        assertEquals(new ItemLook(null, List.of(), false), settings.targets().taggedItem());
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
                  range: lots
                delivery:
                  default-target-item: ""
                inspect:
                  colors: { dropped: "#ABC", lava: "#00ff7f" }
                """);
        List<String> warnings = new ArrayList<>();
        FrameSortSettings settings = parse(yaml, warnings);

        assertEquals("en_US", settings.language());
        assertEquals(HOPPER, settings.sorter().activator());
        assertEquals(20, settings.sorter().tickRate());
        assertEquals(Set.of(EntityType.ITEM_FRAME), settings.sorter().frameTypes());
        assertEquals(64, settings.sorter().range());
        assertNull(settings.delivery().defaultTargetItem());
        assertEquals(Color.fromRGB(0xFFFF55), settings.inspect().colors().dropped());
        assertEquals(Color.fromRGB(0x00FF7F), settings.inspect().colors().lava());
        // default-target-item "" is a valid "off", so it does not warn.
        assertEquals(6, warnings.size(), warnings::toString);
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
            live.set(key, null);
        }
        List<String> warnings = new ArrayList<>();
        ConfigValidator.compare(live, bundled, "config.yml", FrameSortSettings.OPEN_KEYS,
                FrameSortSettings.OPTIONAL_KEYS, warnings::add);
        SorterSettings sorter = parse(live, warnings).sorter();

        assertEquals(List.of(), warnings);
        assertEquals(new ItemLook(null, List.of(), false), parse(live, warnings).targets().taggedItem());
        assertNull(sorter.activatorLook().name());
        assertEquals(List.of(), sorter.activatorLook().lore());
        assertFalse(sorter.activatorLook().glint());
        assertNull(sorter.customName());
    }

    @Test
    void activatorComponentsAreKnownAndChecked() throws Exception {
        YamlConfiguration bundled = bundled("config.yml");
        YamlConfiguration live = bundled("config.yml");
        live.set("sorter.activator.components.item_model", "mypack:sorter");
        live.set("sorter.activator.components.nope", 1);
        List<String> warnings = new ArrayList<>();
        ConfigValidator.compare(live, bundled, "config.yml", FrameSortSettings.OPEN_KEYS,
                FrameSortSettings.OPTIONAL_KEYS, warnings::add);
        assertEquals(List.of(), warnings);

        assertEquals(Set.of("item_model"), parse(live, warnings).sorter().activatorComponents().keySet());
        assertEquals(List.of("config.yml > sorter.activator.components.nope: Unknown item component "
                + "'minecraft:nope', ignored"), warnings);
    }

    @Test
    void aTaggedItemTakesLoreButNoComponents() throws Exception {
        YamlConfiguration bundled = bundled("config.yml");
        YamlConfiguration live = bundled("config.yml");
        live.set("targets.tagged-item.lore", List.of("<gray>Sorted by tag</gray>"));
        List<String> warnings = new ArrayList<>();
        ConfigValidator.compare(live, bundled, "config.yml", FrameSortSettings.OPEN_KEYS,
                FrameSortSettings.OPTIONAL_KEYS, warnings::add);
        assertEquals(List.of(), warnings);
        assertEquals(List.of("<gray>Sorted by tag</gray>"), parse(live, warnings).targets().taggedItem().lore());

        live.set("targets.tagged-item.components.item_model", "mypack:tag");
        ConfigValidator.compare(live, bundled, "config.yml", FrameSortSettings.OPEN_KEYS,
                FrameSortSettings.OPTIONAL_KEYS, warnings::add);
        assertEquals(1, warnings.size(), warnings::toString);
        assertTrue(warnings.getFirst().contains("[targets.tagged-item.components]"), warnings::toString);
    }

    @Test
    void aTypoOfComponentsIsAnUnknownKey() throws Exception {
        YamlConfiguration bundled = bundled("config.yml");
        YamlConfiguration live = bundled("config.yml");
        live.set("sorter.activator.componets.item_model", "mypack:sorter");
        List<String> warnings = new ArrayList<>();
        ConfigValidator.compare(live, bundled, "config.yml", FrameSortSettings.OPEN_KEYS,
                FrameSortSettings.OPTIONAL_KEYS, warnings::add);
        assertEquals(1, warnings.size(), warnings::toString);
        assertTrue(warnings.getFirst().contains("[sorter.activator.componets]"), warnings::toString);
    }

    @Test
    void aCraftEngineActivatorTakesTheLookButNoComponents() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                sorter:
                  activator:
                    material: 'CraftEngine:default:Ruby'
                    name: 'Sorter'
                    glint: true
                    components: {item_model: 'minecraft:stick'}
                """);
        List<String> warnings = new ArrayList<>();
        SorterSettings sorter = parse(yaml, warnings).sorter();

        assertEquals(new ItemReference.Custom("craftengine:default:Ruby"), sorter.activator());
        assertEquals(new ItemLook("Sorter", List.of(), true), sorter.activatorLook());
        assertEquals(Map.of(), sorter.activatorComponents());
        assertEquals(List.of("config.yml > sorter.activator.components: a CraftEngine item keeps its own model and "
                + "components, ignored"), warnings);
    }

    @Test
    void theDefaultTargetItemMayBeACraftEngineItem() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("delivery:\n  default-target-item: 'craftengine:default:trash_bag'\n");
        List<String> warnings = new ArrayList<>();

        assertEquals(new ItemReference.Custom("craftengine:default:trash_bag"),
                parse(yaml, warnings).delivery().defaultTargetItem());
        assertEquals(List.of(), warnings);
    }

    @Test
    void craftEngineItemsWithoutCraftEngineFallBack() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                sorter:
                  activator:
                    material: 'craftengine:default:ruby'
                delivery:
                  default-target-item: 'craftengine:default:trash_bag'
                """);
        List<String> warnings = new ArrayList<>();
        FrameSortSettings settings = FrameSortSettings.parse(
                new ConfigReader(yaml, "config.yml", warnings::add), IS_ITEM, PROBLEM, false);

        assertEquals(HOPPER, settings.sorter().activator());
        assertEquals(FRAME, settings.delivery().defaultTargetItem());
        assertEquals(List.of(
                "config.yml > sorter.activator.material: 'craftengine:default:ruby' needs CraftEngine, which is not "
                        + "installed, using HOPPER",
                "config.yml > delivery.default-target-item: 'craftengine:default:trash_bag' needs CraftEngine, which "
                        + "is not installed, using ITEM_FRAME"), warnings);
    }

    @Test
    void ownershipIsOnByDefaultAndCanBeTurnedOff() throws Exception {
        List<String> warnings = new ArrayList<>();
        assertTrue(parse(bundled("config.yml"), warnings).ownership());

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("ownership: {enabled: false}");
        assertFalse(parse(yaml, warnings).ownership());
        yaml.loadFromString("ownership: {enabled: 'nope'}");
        assertTrue(parse(yaml, warnings).ownership());
        assertEquals(List.of("config.yml > ownership.enabled: expected true or false, using true"), warnings);
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

        assertEquals(HOPPER, settings.sorter().activator());
        assertEquals(FRAME, settings.delivery().defaultTargetItem());
        assertEquals(2, warnings.size(), warnings::toString);
    }
}
