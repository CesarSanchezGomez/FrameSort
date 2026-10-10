package com.cesarcosmico.framesort.config;

import org.bukkit.Color;
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
import java.util.regex.Pattern;

/**
 * Invalid values are reported as {@code file > path: problem} and replaced by the default, so a typo never stops
 * loading.
 */
public final class ConfigReader {

    private static final Pattern HEX_COLOR = Pattern.compile("#?[0-9a-fA-F]{6}");

    private final ConfigurationSection root;
    private final String file;
    private final Consumer<String> warn;

    public ConfigReader(ConfigurationSection root, String file, Consumer<String> warn) {
        this.root = root;
        this.file = file;
        this.warn = warn;
    }

    public void warn(String path, String problem) {
        String current = root.getCurrentPath();
        String fullPath = current == null || current.isEmpty() ? path : current + "." + path;
        warn.accept(file + " > " + fullPath + ": " + problem);
    }

    public boolean isSet(String path) {
        return root.isSet(path);
    }

    public String string(String path, String fallback) {
        String value = root.getString(path);
        return value == null ? fallback : value;
    }

    public List<String> strings(String path) {
        return List.copyOf(root.getStringList(path));
    }

    public boolean bool(String path, boolean fallback) {
        if (!root.isSet(path)) {
            return fallback;
        }
        if (!root.isBoolean(path)) {
            warn(path, "expected true or false, using " + fallback);
            return fallback;
        }
        return root.getBoolean(path);
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

    public double decimal(String path, double fallback, double min, double max) {
        if (!root.isSet(path)) {
            return fallback;
        }
        if (!(root.get(path) instanceof Number number)) {
            warn(path, "expected a number, using " + fallback);
            return fallback;
        }
        double value = number.doubleValue();
        if (!Double.isFinite(value) || value < min || value > max) {
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

    /** Unknown entries are reported and skipped. */
    public List<Material> materials(String path) {
        List<Material> materials = new ArrayList<>();
        for (String name : root.getStringList(path)) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                warn(path, "unknown material '" + name + "', ignored");
            } else {
                materials.add(material);
            }
        }
        return List.copyOf(materials);
    }

    /** A {@code #RRGGBB} colour. */
    public Color color(String path, Color fallback) {
        String value = root.getString(path);
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if (!HEX_COLOR.matcher(trimmed).matches()) {
            warn(path, "'" + value + "' is not a #RRGGBB colour, using " + String.format("#%06X", fallback.asRGB()));
            return fallback;
        }
        return Color.fromRGB(Integer.parseInt(trimmed.startsWith("#") ? trimmed.substring(1) : trimmed, 16));
    }

    /** A namespaced key such as {@code minecraft:stone}; empty means "none". */
    public @Nullable NamespacedKey key(String path) {
        String value = root.getString(path, "");
        if (value.isBlank()) {
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(value.toLowerCase(Locale.ROOT));
        if (key == null) {
            warn(path, "'" + value + "' is not a valid namespaced key, ignored");
        }
        return key;
    }

    /** Matches the constant ignoring case, with {@code -} for {@code _}, so YAML can use kebab-case. */
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
        String constant = name.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (E value : type.getEnumConstants()) {
            if (value.name().equals(constant)) {
                return value;
            }
        }
        return null;
    }
}
