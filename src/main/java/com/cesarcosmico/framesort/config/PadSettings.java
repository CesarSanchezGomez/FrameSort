package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.item.ItemTemplate;
import com.cesarcosmico.framesort.model.ItemReference;
import com.cesarcosmico.framesort.model.PadMode;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

public record PadSettings(PadMode creation, int sweepInterval, Map<String, PadType> types) {

    public PadSettings {
        // Keeps the file's order, so lists and suggestions follow it.
        types = Collections.unmodifiableMap(new LinkedHashMap<>(types));
    }

    /**
     * {@code isBlock} and {@code isItem} need the registries on a server, so tests pass their own; {@code isItem}
     * leaves out air.
     */
    public static PadSettings parse(ConfigReader reader, Predicate<Material> isBlock, Predicate<Material> isItem) {
        Map<String, PadType> types = new LinkedHashMap<>();
        ConfigReader section = reader.section("types");
        if (section != null) {
            for (String id : section.childKeys()) {
                PadType type = parseType(section, id, isBlock, isItem);
                if (type != null) {
                    types.put(type.id(), type);
                }
            }
        }
        if (types.isEmpty()) {
            reader.warn("types", "no valid pad types; teleport pads are disabled");
        }
        return new PadSettings(
                reader.enumValue("creation", PadMode.class, PadMode.ANYONE),
                reader.integer("sweep-interval", 10, 1, 200),
                types);
    }

    public @Nullable PadType type(String id) {
        return types.get(id.toLowerCase(Locale.ROOT));
    }

    private static @Nullable PadType parseType(ConfigReader types, String id, Predicate<Material> isBlock,
                                               Predicate<Material> isItem) {
        ConfigReader reader = types.section(id);
        if (reader == null) {
            types.warn(id, "expected a section with a 'structure' list");
            return null;
        }
        String skipped = "; pad type '" + id + "' is skipped";
        List<ItemReference> structure = new ArrayList<>();
        for (String entry : reader.strings("structure")) {
            ItemReference block = ItemReferences.parse(entry, problem -> reader.warn("structure", problem + skipped));
            if (block == null) {
                return null;
            }
            // Players build a pad by hand, and /framesort give pad hands out its top block.
            if (block instanceof ItemReference.Vanilla(Material material)
                    && (!isBlock.test(material) || !isItem.test(material))) {
                reader.warn("structure", material + " is not a block a player can place" + skipped);
                return null;
            }
            structure.add(block);
        }
        if (structure.size() != 2) {
            reader.warn("structure", "needs exactly two blocks, top then base" + skipped);
            return null;
        }
        ItemReference top = structure.getFirst();
        ConfigReader item = reader.sectionOrEmpty("item");
        ItemTemplate template = null;
        if (top instanceof ItemReference.Vanilla(Material material)) {
            if (item.isSet("components")) {
                item.warn("components", "a pad item looks like the block it places, ignored");
            }
            template = new ItemTemplate(material, Map.of(), ItemLookParser.look(item));
        } else {
            String look = ItemReferences.lookKeys(item);
            if (look != null) {
                reader.warn("item", "a CraftEngine block keeps its own look; " + look + " ignored");
            }
        }
        return new PadType(id.toLowerCase(Locale.ROOT), top, structure.get(1), template);
    }
}
