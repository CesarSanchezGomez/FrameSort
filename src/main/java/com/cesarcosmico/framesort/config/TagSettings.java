package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.model.ItemReference;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public record TagSettings(Map<NamespacedKey, CustomTag> tags) {

    public record CustomTag(List<Material> items, List<NamespacedKey> gameTags, List<String> customItems) {
        public CustomTag {
            items = List.copyOf(items);
            gameTags = List.copyOf(gameTags);
            customItems = List.copyOf(customItems);
        }
    }

    public TagSettings {
        tags = Map.copyOf(tags);
    }

    /** {@code isItem} leaves out air. */
    public static TagSettings parse(ConfigReader reader, Predicate<Material> isItem,
                                    Predicate<NamespacedKey> isGameTag, boolean craftEngine) {
        ConfigReader section = reader.section("tags");
        if (section == null) {
            return new TagSettings(Map.of());
        }
        Map<NamespacedKey, CustomTag> tags = new HashMap<>();
        Set<NamespacedKey> seen = new HashSet<>();
        for (String name : section.childKeys()) {
            NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
            if (key == null) {
                section.warn(name, "not a valid tag name, such as 'axes' or 'server:gems'; ignored");
            } else if (!seen.add(key)) {
                section.warn(name, "names " + key.asString() + " again; only the first one counts");
            } else {
                CustomTag tag = parseTag(section, name, isItem, isGameTag, craftEngine);
                if (tag != null) {
                    tags.put(key, tag);
                }
            }
        }
        return new TagSettings(tags);
    }

    private static @Nullable CustomTag parseTag(ConfigReader tags, String name, Predicate<Material> isItem,
                                                Predicate<NamespacedKey> isGameTag, boolean craftEngine) {
        List<Material> items = new ArrayList<>();
        List<NamespacedKey> gameTags = new ArrayList<>();
        List<String> customItems = new ArrayList<>();
        for (String entry : tags.strings(name)) {
            String member = entry.trim();
            if (member.startsWith("#")) {
                NamespacedKey include = NamespacedKey.fromString(member.substring(1).toLowerCase(Locale.ROOT));
                if (include == null) {
                    tags.warn(name, "'" + entry + "' is not a valid tag, such as '#minecraft:axes'; ignored");
                } else if (!isGameTag.test(include)) {
                    tags.warn(name, "the game has no tag called '" + member + "'; ignored");
                } else {
                    gameTags.add(include);
                }
                continue;
            }
            ItemReference reference = ItemReferences.parse(member, craftEngine,
                    problem -> tags.warn(name, problem + "; ignored"));
            if (reference instanceof ItemReference.Custom(String id)) {
                customItems.add(id);
            } else if (reference instanceof ItemReference.Vanilla(Material material)) {
                if (isItem.test(material)) {
                    items.add(material);
                } else {
                    tags.warn(name, "'" + entry + "' is not an item; ignored");
                }
            }
        }
        if (items.isEmpty() && gameTags.isEmpty() && customItems.isEmpty()) {
            tags.warn(name, "no valid members (items, '#tags' or 'craftengine:<namespace>:<id>'); tag ignored");
            return null;
        }
        return new CustomTag(items, gameTags, customItems);
    }
}
