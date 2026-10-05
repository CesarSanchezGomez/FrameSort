package com.cesarcosmico.framesort.config;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Typed reads from a YAML section. An invalid value is reported once, with its path, and replaced by the
 * default, so one typo never stops the plugin from loading.
 */
public final class ConfigReader {

    private final ConfigurationSection root;
    private final String file;
    private final Consumer<String> warn;

    public ConfigReader(ConfigurationSection root, String file, Consumer<String> warn) {
        this.root = root;
        this.file = file;
        this.warn = warn;
    }

    public void warn(String path, String problem) {
        String prefix = root.getCurrentPath() == null || root.getCurrentPath().isEmpty()
                ? path : root.getCurrentPath() + "." + path;
        warn.accept(file + " > " + prefix + ": " + problem);
    }

    public String string(String path, String fallback) {
        String value = root.getString(path);
        return value == null ? fallback : value;
    }

    public List<String> strings(String path) {
        return List.copyOf(root.getStringList(path));
    }

    public boolean bool(String path, boolean fallback) {
        if (root.isSet(path) && !root.isBoolean(path)) {
            warn(path, "expected true or false, using " + fallback);
            return fallback;
        }
        return root.getBoolean(path, fallback);
    }

    public int integer(String path, int fallback, int min, int max) {
        if (!root.isSet(path)) {
            return fallback;
        }
        if (!root.isInt(path)) {
            warn(path, "expected a whole number, using " + fallback);
            return fallback;
        }
        int value = root.getInt(path);
        if (value < min || value > max) {
            warn(path, value + " is outside " + min + ".." + max + ", using " + fallback);
            return fallback;
        }
        return value;
    }

    /** A material name. An empty value means "none" and returns {@code null}. */
    public @Nullable Material material(String path, @Nullable Material fallback) {
        String name = root.getString(path);
        if (name == null) {
            return fallback;
        }
        if (name.isBlank()) {
            return null;
        }
        Material material = Material.matchMaterial(name);
        if (material == null) {
            warn(path, "unknown material '" + name + "', using " + (fallback == null ? "none" : fallback));
            return fallback;
        }
        return material;
    }

    /** Like {@link #material} but never empty: blank or unknown values fall back. */
    public Material requiredMaterial(String path, Material fallback) {
        Material material = material(path, fallback);
        if (material == null) {
            warn(path, "a material is required, using " + fallback);
            return fallback;
        }
        return material;
    }

    public List<Material> materials(String path) {
        List<Material> materials = new ArrayList<>();
        for (String name : root.getStringList(path)) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                warn(path, "unknown material '" + name + "'");
            } else {
                materials.add(material);
            }
        }
        return List.copyOf(materials);
    }

    public @Nullable NamespacedKey key(String path) {
        String value = root.getString(path, "");
        if (value.isBlank()) {
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(value.toLowerCase(Locale.ROOT));
        if (key == null) {
            warn(path, "'" + value + "' is not a valid namespaced key");
        }
        return key;
    }

    public <E extends Enum<E>> E enumValue(String path, Class<E> type, E fallback) {
        String name = root.getString(path);
        if (name == null) {
            return fallback;
        }
        E value = parseEnum(type, name);
        if (value == null) {
            warn(path, "unknown value '" + name + "', using " + fallback.name().toLowerCase(Locale.ROOT));
            return fallback;
        }
        return value;
    }

    /** Unknown entries are reported and skipped; a missing or fully invalid list falls back. */
    public <E extends Enum<E>> Set<E> enumSet(String path, Class<E> type, Set<E> fallback) {
        if (!root.isSet(path)) {
            return fallback;
        }
        Set<E> values = EnumSet.noneOf(type);
        for (String name : root.getStringList(path)) {
            E value = parseEnum(type, name);
            if (value == null) {
                warn(path, "unknown value '" + name + "', ignored");
            } else {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            warn(path, "no valid values, using " + fallback);
            return fallback;
        }
        return Set.copyOf(values);
    }

    /** The reader for a child section, or {@code null} when it does not exist. */
    public @Nullable ConfigReader section(String path) {
        ConfigurationSection section = root.getConfigurationSection(path);
        return section == null ? null : new ConfigReader(section, file, warn);
    }

    /** The reader for a child section, or an empty one so every read falls back to its default. */
    public ConfigReader sectionOrEmpty(String path) {
        ConfigurationSection section = root.getConfigurationSection(path);
        return new ConfigReader(section == null ? new MemoryConfiguration() : section, file, warn);
    }

    public Set<String> childKeys() {
        return root.getKeys(false);
    }

    private static <E extends Enum<E>> @Nullable E parseEnum(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
