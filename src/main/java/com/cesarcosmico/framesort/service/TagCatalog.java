package com.cesarcosmico.framesort.service;

import com.cesarcosmico.framesort.config.TagSettings;
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

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/** Tags by name, from tags.yml and from Paper's item and block registries, cached until the next reload. */
public final class TagCatalog {

    public enum Kind { ITEM, BLOCK, CUSTOM }

    public record TagView(NamespacedKey key, Kind kind, List<Material> materials, Set<Material> lookup,
                          List<String> customItems) {
        public TagView {
            materials = List.copyOf(materials);
            lookup = Set.copyOf(lookup);
            customItems = List.copyOf(customItems);
        }
    }

    private static final Comparator<Material> BY_KEY = Comparator.comparing(material -> material.getKey().asString());

    private final Supplier<TagSettings> settings;
    private final Map<NamespacedKey, TagView> found = new HashMap<>();
    private final Set<NamespacedKey> missing = new HashSet<>();
    private @Nullable List<NamespacedKey> names;

    public TagCatalog(Supplier<TagSettings> settings) {
        this.settings = settings;
    }

    /** A tags.yml tag replaces the game's tag of its name; an item tag wins over a block tag: frames sort items. */
    public @Nullable TagView find(NamespacedKey key) {
        TagView cached = found.get(key);
        if (cached != null || missing.contains(key)) {
            return cached;
        }
        TagSettings.CustomTag custom = settings.get().tags().get(key);
        TagView view = custom == null ? gameTag(key) : fromFile(key, custom);
        if (view == null) {
            missing.add(key);
        } else {
            found.put(key, view);
        }
        return view;
    }

    public List<NamespacedKey> names() {
        if (names == null) {
            Set<NamespacedKey> all = new TreeSet<>(Comparator.comparing(NamespacedKey::asString));
            all.addAll(settings.get().tags().keySet());
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

    /** Whether the game has an item or block tag called {@code key}, whatever tags.yml says. */
    public static boolean isGameTag(NamespacedKey key) {
        return gameTag(key) != null;
    }

    private static TagView fromFile(NamespacedKey key, TagSettings.CustomTag tag) {
        Set<Material> materials = new TreeSet<>(BY_KEY);
        materials.addAll(tag.items());
        for (NamespacedKey include : tag.includes()) {
            // tags.yml checked it on load, but a datapack reload can drop a game tag after that.
            TagView game = gameTag(include);
            if (game != null) {
                materials.addAll(game.materials());
            }
        }
        List<String> customItems = tag.customItems().stream().distinct().sorted().toList();
        return new TagView(key, Kind.CUSTOM, List.copyOf(materials), materials, customItems);
    }

    /** The blocks of the game's block tag {@code key}, or {@code null} when the game has no such tag. */
    public static @Nullable Set<Material> blockTag(NamespacedKey key) {
        TagView view = load(RegistryKey.BLOCK, key, Kind.BLOCK);
        return view == null ? null : view.lookup();
    }

    private static @Nullable TagView gameTag(NamespacedKey key) {
        TagView view = load(RegistryKey.ITEM, key, Kind.ITEM);
        return view != null ? view : load(RegistryKey.BLOCK, key, Kind.BLOCK);
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
                .sorted(BY_KEY)
                .toList();
        return new TagView(key, kind, materials, Set.copyOf(materials), List.of());
    }

    private static <T extends Keyed> void collect(RegistryKey<T> registryKey, Set<NamespacedKey> into) {
        Registry<T> registry = RegistryAccess.registryAccess().getRegistry(registryKey);
        registry.getTags().forEach(tag -> {
            Key key = tag.tagKey().key();
            into.add(new NamespacedKey(key.namespace(), key.value()));
        });
    }
}
