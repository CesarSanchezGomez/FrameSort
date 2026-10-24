package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.item.ItemLook;
import com.cesarcosmico.framesort.item.ItemTemplate;
import com.cesarcosmico.framesort.item.Keys;
import com.cesarcosmico.framesort.item.MarkerCodec;
import com.cesarcosmico.framesort.model.ItemReference;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A sorter is one of the {@code blocks}, placed from a sorter item that looks like {@code item}; {@code recipe} crafts
 * one, or is {@code null} when only /framesort give hands them out. A vanilla activator is built from its material,
 * {@code activatorComponents} and {@code activatorLook}; a CraftEngine one is built by CraftEngine, with
 * {@code activatorLook} laid over it and no components.
 */
public record SorterSettings(Set<Material> blocks, ItemLook item, @Nullable Recipe recipe, ItemReference activator,
                             ItemLook activatorLook, Map<String, String> activatorComponents, boolean requireMarked,
                             @Nullable String customName, int range, int tickRate, boolean disableWhenPowered,
                             boolean showActivity) {

    /** Rows of the crafting grid: {@code C} is the container, {@code A} the activator, the rest {@code ingredients}. */
    public record Recipe(List<String> shape, Map<Character, ItemReference> ingredients) {

        public static final char CONTAINER = 'C';
        public static final char ACTIVATOR = 'A';

        public Recipe {
            shape = List.copyOf(shape);
            ingredients = Map.copyOf(ingredients);
        }

        public boolean uses(char letter) {
            return shape.stream().anyMatch(row -> row.indexOf(letter) >= 0);
        }
    }

    private static final ItemReference.Vanilla DEFAULT_ACTIVATOR = new ItemReference.Vanilla(Material.HOPPER);
    private static final List<String> DEFAULT_BLOCKS = List.of("DISPENSER", "DROPPER", "BARREL", "CHEST",
            "TRAPPED_CHEST", "#minecraft:copper_chests", "#minecraft:shulker_boxes");
    private static final List<String> DEFAULT_SHAPE = List.of("A", "C");
    private static final String USING_DEFAULT_SHAPE = ", using ['A', 'C']";

    public SorterSettings {
        blocks = Collections.unmodifiableSet(new LinkedHashSet<>(blocks));
        activatorComponents = Collections.unmodifiableMap(new LinkedHashMap<>(activatorComponents));
    }

    /**
     * {@code isContainer} says whether a material's block holds an inventory, and {@code blockTag} gives the blocks of
     * a game block tag, or {@code null} when there is none; both need a server, so tests pass their own.
     */
    static SorterSettings parse(ConfigReader reader, Predicate<Material> isItem, Predicate<Material> isContainer,
                                Function<NamespacedKey, @Nullable Set<Material>> blockTag,
                                Function<String, @Nullable String> problem, boolean craftEngine) {
        ConfigReader activator = reader.sectionOrEmpty("activator");
        ItemReference item = ItemReferences.item(activator, "material",
                activator.string("material", DEFAULT_ACTIVATOR.material().name()), DEFAULT_ACTIVATOR, isItem,
                craftEngine);
        ItemLook look;
        Map<String, String> components;
        if (item instanceof ItemReference.Vanilla(Material material)) {
            ItemTemplate template = ItemLookParser.template(activator, material, problem);
            look = template.look();
            components = template.components();
        } else {
            ItemReferences.ignoreComponents(activator);
            look = ItemLookParser.look(activator);
            components = Map.of();
        }
        String customName = reader.string("custom-name", "");
        return new SorterSettings(
                blocks(reader, isContainer, blockTag, craftEngine),
                ItemLookParser.look(reader.sectionOrEmpty("item")),
                recipe(reader.sectionOrEmpty("recipe"), isItem, craftEngine),
                item,
                look,
                components,
                activator.bool("require-marked", false),
                customName.isBlank() ? null : customName,
                reader.integer("range", 64, 1, 512),
                reader.integer("tick-rate", 20, 1, 1200),
                reader.bool("disable-when-powered", false),
                reader.bool("show-activity", true));
    }

    /** Whether {@code item} works as the activator; {@code customId} gives a custom item's reference, or null. */
    public boolean isActivator(ItemStack item, Function<ItemStack, @Nullable String> customId) {
        return activator.matches(item.getType(), customId.apply(item))
                && (!requireMarked || MarkerCodec.read(item, Keys.ACTIVATOR) != null);
    }

    private static Set<Material> blocks(ConfigReader reader, Predicate<Material> isContainer,
                                        Function<NamespacedKey, @Nullable Set<Material>> blockTag,
                                        boolean craftEngine) {
        List<String> entries = DEFAULT_BLOCKS;
        if (reader.isSet("blocks")) {
            if (reader.value("blocks") instanceof List<?>) {
                entries = reader.strings("blocks");
            } else {
                reader.warn("blocks", "expected a list of containers, using the default ones");
            }
        }
        Set<Material> blocks = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry.startsWith("#")) {
                NamespacedKey key = NamespacedKey.fromString(entry.substring(1));
                Set<Material> tagged = key == null ? null : blockTag.apply(key);
                if (tagged == null) {
                    reader.warn("blocks", "the game has no block tag '" + entry + "'; ignored");
                    continue;
                }
                tagged.stream().filter(isContainer).forEach(blocks::add);
                continue;
            }
            ItemReference block = ItemReferences.parse(entry, craftEngine,
                    problem -> reader.warn("blocks", problem + "; ignored"));
            if (block == null) {
                continue;
            }
            // A CraftEngine block shows to the server as a vanilla block that holds no items.
            if (!(block instanceof ItemReference.Vanilla(Material material)) || !isContainer.test(material)) {
                reader.warn("blocks", "'" + entry + "' is not a container; ignored");
                continue;
            }
            blocks.add(material);
        }
        if (blocks.isEmpty()) {
            reader.warn("blocks", "no valid containers; sorters are disabled");
        }
        return blocks;
    }

    private static @Nullable Recipe recipe(ConfigReader reader, Predicate<Material> isItem, boolean craftEngine) {
        List<String> shape = DEFAULT_SHAPE;
        if (reader.isSet("shape")) {
            if (!(reader.value("shape") instanceof List<?>)) {
                reader.warn("shape", "expected a list of rows" + USING_DEFAULT_SHAPE);
            } else if (reader.strings("shape").isEmpty()) {
                return null;
            } else {
                String problem = shapeProblem(reader.strings("shape"));
                if (problem == null) {
                    shape = reader.strings("shape");
                } else {
                    reader.warn("shape", problem + USING_DEFAULT_SHAPE);
                }
            }
        }
        Map<Character, ItemReference> ingredients = new LinkedHashMap<>();
        ConfigReader section = reader.sectionOrEmpty("ingredients");
        for (String key : section.childKeys()) {
            if (key.length() != 1 || key.equals(" ") || key.charAt(0) == Recipe.CONTAINER
                    || key.charAt(0) == Recipe.ACTIVATOR) {
                section.warn(key, "an ingredient is one letter other than 'C' and 'A'; ignored");
                continue;
            }
            char letter = key.charAt(0);
            if (shape.stream().noneMatch(row -> row.indexOf(letter) >= 0)) {
                section.warn(key, "not in the shape; ignored");
                continue;
            }
            ItemReference ingredient = ItemReferences.parse(section.string(key, ""), craftEngine,
                    problem -> section.warn(key, problem + "; ignored"));
            if (ingredient instanceof ItemReference.Vanilla(Material material) && !isItem.test(material)) {
                section.warn(key, material + " is not an item; ignored");
            } else if (ingredient != null) {
                ingredients.put(letter, ingredient);
            }
        }
        for (String row : shape) {
            for (char letter : row.toCharArray()) {
                if (letter != ' ' && letter != Recipe.CONTAINER && letter != Recipe.ACTIVATOR
                        && !ingredients.containsKey(letter)) {
                    reader.warn("shape", "'" + letter + "' is not a usable ingredient" + USING_DEFAULT_SHAPE);
                    return new Recipe(DEFAULT_SHAPE, Map.of());
                }
            }
        }
        return new Recipe(shape, ingredients);
    }

    // The crafting grid takes one to three rows of the same length, up to three wide.
    private static @Nullable String shapeProblem(List<String> shape) {
        if (shape.size() > 3 || shape.stream().anyMatch(row -> row.isEmpty() || row.length() > 3)) {
            return "a shape is one to three rows of one to three letters";
        }
        if (shape.stream().map(String::length).distinct().count() > 1) {
            return "every row needs the same length";
        }
        long containers = shape.stream().flatMapToInt(String::chars).filter(c -> c == Recipe.CONTAINER).count();
        return containers == 1 ? null : "'C', the container, has to be in the shape exactly once";
    }
}
