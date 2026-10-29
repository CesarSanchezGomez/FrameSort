package com.cesarcosmico.framesort.config;

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
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public record PadSettings(PadMode creation, int sweepInterval, Map<String, PadType> types) {

    // Each type is an entry the admin names, so only its parser knows its keys and can report a typo in one.
    private static final Set<String> TYPE_KEYS = Set.of("structure", "range", "pull-radius", "takes-from-inventory",
            "item");
    private static final Set<String> ITEM_KEYS = Set.of("name", "lore", "glint", "components");

    public PadSettings {
        // Keeps the file's order, so lists and suggestions follow it.
        types = Collections.unmodifiableMap(new LinkedHashMap<>(types));
    }

    /** {@code isItem} leaves out air. */
    public static PadSettings parse(ConfigReader reader, Predicate<Material> isBlock, Predicate<Material> isItem,
                                    boolean craftEngine) {
        Map<String, PadType> types = new LinkedHashMap<>();
        ConfigReader section = reader.section("types");
        if (section != null) {
            for (String id : section.childKeys()) {
                String lowerId = id.toLowerCase(Locale.ROOT);
                if (types.containsKey(lowerId)) {
                    section.warn(id, "names " + lowerId + " again; only the first one counts");
                    continue;
                }
                PadType type = parseType(section, id, isBlock, isItem, craftEngine);
                if (type != null) {
                    types.put(type.id(), type);
                }
            }
        }
        if (types.isEmpty()) {
            reader.warn("types", "no valid pad types; pads are disabled");
        }
        return new PadSettings(
                reader.enumValue("creation", PadMode.class, PadMode.ANYONE),
                reader.integer("sweep-interval", 10, 1, 200),
                types);
    }

    public @Nullable PadType type(String id) {
        return types.get(id.toLowerCase(Locale.ROOT));
    }

    public void warnTopChanges(PadSettings before, Consumer<String> warn) {
        for (PadType type : types.values()) {
            PadType old = before.type(type.id());
            if (old != null && !old.top().equals(type.top())) {
                warn.accept("pads.yml > types." + type.id() + ".structure: the top block changed; pads of this type"
                        + " built with the old one are forgotten at their next check");
            }
        }
    }

    private static @Nullable PadType parseType(ConfigReader types, String id, Predicate<Material> isBlock,
                                               Predicate<Material> isItem, boolean craftEngine) {
        if (id.contains(",")) {
            types.warn(id, "a pad type id can't contain ','; pad type '" + id + "' is skipped");
            return null;
        }
        ConfigReader reader = types.section(id);
        if (reader == null) {
            types.warn(id, "expected a section with a 'structure' list");
            return null;
        }
        reader.warnUnknownKeys(TYPE_KEYS);
        String skipped = "; pad type '" + id + "' is skipped";
        List<ItemReference> structure = new ArrayList<>();
        for (String entry : reader.strings("structure")) {
            ItemReference block = ItemReferences.parse(entry, craftEngine,
                    problem -> reader.warn("structure", problem + skipped));
            if (block == null) {
                return null;
            }
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
        item.warnUnknownKeys(ITEM_KEYS);
        if (top instanceof ItemReference.Custom) {
            ItemReferences.ignoreComponents(item);
        } else if (item.isSet("components")) {
            item.warn("components", "a pad item looks like the block it places, ignored");
        }
        return new PadType(id.toLowerCase(Locale.ROOT), top, structure.get(1), required(reader, "range", 64, 1, 512),
                required(reader, "pull-radius", 4, 0, 16), required(reader, "takes-from-inventory", false),
                ItemLookParser.look(item));
    }

    // Each type sets these itself, so a missing one changes what its pads do and is reported like a wrong one.
    private static int required(ConfigReader reader, String path, int fallback, int min, int max) {
        warnIfMissing(reader, path, fallback);
        return reader.integer(path, fallback, min, max);
    }

    private static boolean required(ConfigReader reader, String path, boolean fallback) {
        warnIfMissing(reader, path, fallback);
        return reader.bool(path, fallback);
    }

    private static void warnIfMissing(ConfigReader reader, String path, Object fallback) {
        if (!reader.isSet(path)) {
            reader.warn(path, "missing, using " + fallback);
        }
    }
}
