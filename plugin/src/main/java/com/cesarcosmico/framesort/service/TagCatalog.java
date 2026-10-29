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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/** Tags by name, from tags.yml and from Paper's item and block registries, read again on every reload. */
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

    private record Snapshot(Map<NamespacedKey, TagView> views, List<NamespacedKey> names, Set<Material> shulkerBoxes) {
    }

    private static final Comparator<Material> BY_KEY = Comparator.comparing(material -> material.getKey().asString());
    private static final NamespacedKey SHULKER_BOXES = NamespacedKey.minecraft("shulker_boxes");

    private final Supplier<TagSettings> settings;
    // Built on the main thread and swapped whole: command parsing can read it from Paper's suggestion thread.
    private volatile Snapshot snapshot;

    public TagCatalog(Supplier<TagSettings> settings) {
        this.settings = settings;
        this.snapshot = build(settings.get());
    }

    /** A tag in tags.yml wins over a game tag of the same name, and an item tag over a block tag: frames sort items. */
    public @Nullable TagView find(NamespacedKey key) {
        return snapshot.views().get(key);
    }

    public List<NamespacedKey> names() {
        return snapshot.names();
    }

    /** Reads tags.yml and the game's tags again; on the main thread, because it reads the registries. */
    public void reload() {
        snapshot = build(settings.get());
    }

    /** Whether {@code material} is a shulker box, by the game's own tag whatever tags.yml says. */
    public boolean isShulkerBox(Material material) {
        return snapshot.shulkerBoxes().contains(material);
    }

    /** Whether the game has an item or block tag called {@code key}, whatever tags.yml says. */
    public static boolean isGameTag(NamespacedKey key) {
        return hasTag(RegistryKey.ITEM, key) || hasTag(RegistryKey.BLOCK, key);
    }

    private static Snapshot build(TagSettings settings) {
        Map<NamespacedKey, TagView> game = new HashMap<>();
        collect(RegistryKey.BLOCK, Kind.BLOCK, game);
        collect(RegistryKey.ITEM, Kind.ITEM, game);
        Map<NamespacedKey, TagView> views = new HashMap<>(game);
        settings.tags().forEach((key, custom) -> views.put(key, fromFile(key, custom, game)));
        List<NamespacedKey> names = views.keySet().stream()
                .sorted(Comparator.comparing(NamespacedKey::asString))
                .toList();
        TagView shulkers = game.get(SHULKER_BOXES);
        return new Snapshot(Map.copyOf(views), names, shulkers == null ? Set.of() : shulkers.lookup());
    }

    private static TagView fromFile(NamespacedKey key, TagSettings.CustomTag tag, Map<NamespacedKey, TagView> game) {
        Set<Material> materials = new TreeSet<>(BY_KEY);
        materials.addAll(tag.items());
        for (NamespacedKey gameTagKey : tag.gameTags()) {
            // tags.yml checked it on load, but a datapack reload can drop a game tag after that.
            TagView gameTag = game.get(gameTagKey);
            if (gameTag != null) {
                materials.addAll(gameTag.materials());
            }
        }
        List<String> customItems = tag.customItems().stream().distinct().sorted().toList();
        return new TagView(key, Kind.CUSTOM, List.copyOf(materials), materials, customItems);
    }

    // Puts every tag of the registry, so an item tag collected after a block one replaces it.
    private static <T extends Keyed> void collect(RegistryKey<T> registryKey, Kind kind,
                                                  Map<NamespacedKey, TagView> into) {
        Registry<T> registry = RegistryAccess.registryAccess().getRegistry(registryKey);
        registry.getTags().forEach(tag -> {
            Key key = tag.tagKey().key();
            NamespacedKey name = new NamespacedKey(key.namespace(), key.value());
            List<Material> materials = tag.values().stream()
                    .map(TypedKey::key)
                    .map(member -> Material.matchMaterial(member.asString()))
                    .filter(Objects::nonNull)
                    .sorted(BY_KEY)
                    .toList();
            into.put(name, new TagView(name, kind, materials, Set.copyOf(materials), List.of()));
        });
    }

    private static <T extends Keyed> boolean hasTag(RegistryKey<T> registryKey, NamespacedKey key) {
        return RegistryAccess.registryAccess().getRegistry(registryKey).hasTag(TagKey.create(registryKey, key));
    }
}
