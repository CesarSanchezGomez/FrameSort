package com.cesarcosmico.framesort.config;

import com.cesarcosmico.framesort.model.ItemReference;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Reads an item or block named in the config. The plugin name before the first {@code :} is matched ignoring case;
 * what follows is CraftEngine's ID, exactly as written. Only the syntax is checked: CraftEngine may not have loaded
 * its items yet.
 */
final class ItemReferences {

    private ItemReferences() {
    }

    /** The reference, or {@code null} after telling {@code problem} why {@code text} names nothing usable. */
    static @Nullable ItemReference parse(String text, Consumer<String> problem) {
        String value = text.trim();
        int colon = value.indexOf(':');
        String plugin = colon < 0 ? NamespacedKey.MINECRAFT : value.substring(0, colon).toLowerCase(Locale.ROOT);
        String id = value.substring(colon + 1);
        if (plugin.isEmpty() || id.isEmpty()) {
            problem.accept("'" + text + "' is not a material or 'craftengine:<namespace>:<id>'");
            return null;
        }
        if (plugin.equals(NamespacedKey.MINECRAFT)) {
            Material material = Material.matchMaterial(id);
            if (material == null) {
                problem.accept("unknown material '" + text + "'");
                return null;
            }
            return new ItemReference.Vanilla(material);
        }
        if (plugin.equals(ItemReference.CRAFT_ENGINE)) {
            int split = id.indexOf(':');
            // Every CraftEngine ID has a namespace and two packs can share a value, so 'default' is never assumed.
            if (split <= 0 || split == id.length() - 1) {
                problem.accept("'" + text + "' needs its namespace, such as 'craftengine:default:topaz_axe'");
                return null;
            }
            return new ItemReference.Custom(ItemReference.CRAFT_ENGINE + ":" + id);
        }
        problem.accept("'" + text + "': FrameSort reads no items from '" + plugin
                + "', only 'craftengine:<namespace>:<id>'");
        return null;
    }
}
