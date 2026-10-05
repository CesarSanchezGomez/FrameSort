package com.cesarcosmico.framesort.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Compares a YAML file with the copy bundled in the jar and warns about an outdated {@code config-version},
 * missing keys and unknown keys (typos or removed settings). It never changes the file.
 */
public final class ConfigValidator {

    private static final String VERSION_KEY = "config-version";

    private ConfigValidator() {
    }

    public static void check(Plugin plugin, ConfigurationSection live, String resource, Consumer<String> warn) {
        check(plugin, live, resource, Set.of(), warn);
    }

    /**
     * Same as {@link #check(Plugin, ConfigurationSection, String, Consumer)}, but keys under the {@code open}
     * sections are user-defined (pad types, for example) and are neither missing nor unknown.
     */
    public static void check(Plugin plugin, ConfigurationSection live, String resource, Set<String> open,
                             Consumer<String> warn) {
        YamlConfiguration bundled = ConfigFiles.bundled(plugin, resource);
        if (bundled == null) {
            return;
        }
        int expected = bundled.getInt(VERSION_KEY, 0);
        int current = live.getInt(VERSION_KEY, 0);
        if (current < expected) {
            warn.accept(resource + " is outdated (config-version " + current + ", expected " + expected
                    + "); compare it with the default file.");
        }

        List<String> missing = new ArrayList<>();
        for (String key : bundled.getKeys(true)) {
            if (!bundled.isConfigurationSection(key) && !live.isSet(key) && !isOpen(key, open)) {
                missing.add(key);
            }
        }
        Set<String> unknown = new HashSet<>();
        for (String key : live.getKeys(true)) {
            if (!bundled.contains(key) && !isOpen(key, open)) {
                unknown.add(key);
            }
        }
        // Report only the top-most unknown key, not every child under it.
        unknown.removeIf(key -> hasUnknownParent(key, unknown));

        if (!missing.isEmpty()) {
            warn.accept(resource + " is missing " + missing.size() + " key(s), defaults are used: " + missing);
        }
        if (!unknown.isEmpty()) {
            warn.accept(resource + " has unknown key(s), typo or removed setting: " + unknown);
        }
    }

    private static boolean isOpen(String key, Set<String> open) {
        for (String section : open) {
            if (key.startsWith(section + ".")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUnknownParent(String key, Set<String> unknown) {
        for (int dot = key.lastIndexOf('.'); dot > 0; dot = key.lastIndexOf('.', dot - 1)) {
            if (unknown.contains(key.substring(0, dot))) {
                return true;
            }
        }
        return false;
    }
}
