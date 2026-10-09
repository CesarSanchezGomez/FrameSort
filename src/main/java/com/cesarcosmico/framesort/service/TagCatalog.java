package com.cesarcosmico.framesort.service;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.tag.TagKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Item and block tags by name, read from Paper's registries and cached until the next reload. */
public final class TagCatalog {

    public enum Kind { ITEM, BLOCK }

    // Minecraft's AnvilMenu.MAX_NAME_LENGTH: a name this long may have been cut by the anvil, so only such a name is
    // completed to the tag it starts like. Shorter names never are, so "#log" does not turn into "#logs".
    private static final int ANVIL_NAME_LIMIT = 50;

    public record TagView(NamespacedKey key, Kind kind, List<Material> materials, Set<Material> lookup) {
        public TagView {
            materials = List.copyOf(materials);
            lookup = Set.copyOf(lookup);
        }
    }

    private final Map<NamespacedKey, TagView> found = new HashMap<>();
    private final Set<NamespacedKey> missing = new HashSet<>();
    private @Nullable List<NamespacedKey> names;

    /** An item tag wins over a block tag with the same name: frames sort items. */
    public @Nullable TagView find(NamespacedKey key) {
        TagView cached = found.get(key);
        if (cached != null || missing.contains(key)) {
            return cached;
        }
        TagView view = load(RegistryKey.ITEM, key, Kind.ITEM);
        if (view == null) {
            view = load(RegistryKey.BLOCK, key, Kind.BLOCK);
        }
        if (view == null) {
            missing.add(key);
        } else {
            found.put(key, view);
        }
        return view;
    }

    /** The tag a frame item's name stands for: {@link #parseName}, or {@link #complete} when the anvil cut it. */
    public @Nullable TagView resolve(String name) {
        NamespacedKey key = parseName(name);
        if (key == null) {
            return null;
        }
        TagView view = find(key);
        if (view != null) {
            return view;
        }
        NamespacedKey completed = complete(name, names());
        return completed == null ? null : find(completed);
    }

    public List<NamespacedKey> names() {
        if (names == null) {
            Set<NamespacedKey> all = new TreeSet<>(Comparator.comparing(NamespacedKey::asString));
            collect(RegistryKey.ITEM, all);
            collect(RegistryKey.BLOCK, all);
            names = List.copyOf(all);
        }
        return names;
    }

    public void clear() {
        found.clear();
        missing.clear();
        names = null;
    }

    /**
     * A frame item's name as a tag: {@code #logs} and {@code #minecraft:logs} are the same tag. Without the leading
     * {@code #}, as in Minecraft's own tag syntax, a name is just a name.
     */
    static @Nullable NamespacedKey parseName(String name) {
        String trimmed = name.trim().toLowerCase(Locale.ROOT);
        if (!trimmed.startsWith("#") || trimmed.length() == 1) {
            return null;
        }
        return NamespacedKey.fromString(trimmed.substring(1));
    }

    /** The only tag in {@code names} that a name cut by the anvil starts like, or {@code null}. */
    static @Nullable NamespacedKey complete(String name, Collection<NamespacedKey> names) {
        NamespacedKey partial = name.length() < ANVIL_NAME_LIMIT ? null : parseName(name);
        if (partial == null) {
            return null;
        }
        NamespacedKey match = null;
        for (NamespacedKey candidate : names) {
            if (candidate.asString().startsWith(partial.asString())) {
                if (match != null) {
                    return null;
                }
                match = candidate;
            }
        }
        return match;
    }

    private static <T extends Keyed> @Nullable TagView load(RegistryKey<T> registryKey, NamespacedKey key, Kind kind) {
        Registry<T> registry = RegistryAccess.registryAccess().getRegistry(registryKey);
        TagKey<T> tagKey = TagKey.create(registryKey, key);
        if (!registry.hasTag(tagKey)) {
            return null;
        }
        List<Material> materials = registry.getTag(tagKey).values().stream()
                .map(TypedKey::key)
                .map(member -> Material.matchMaterial(member.asString()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(material -> material.getKey().asString()))
                .toList();
        return new TagView(key, kind, materials, Set.copyOf(materials));
    }

    private static <T extends Keyed> void collect(RegistryKey<T> registryKey, Set<NamespacedKey> into) {
        Registry<T> registry = RegistryAccess.registryAccess().getRegistry(registryKey);
        registry.getTags().forEach(tag -> {
            Key key = tag.tagKey().key();
            into.add(new NamespacedKey(key.namespace(), key.value()));
        });
    }
}
