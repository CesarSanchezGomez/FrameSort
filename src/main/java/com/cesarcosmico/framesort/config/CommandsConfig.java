package com.cesarcosmico.framesort.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * {@code commands.yml}: each top-level section is a feature, keyed by a fixed id, with every path that runs it.
 * The bundled file decides which features exist; the server's file overrides their values. Commands register once,
 * so edits need a restart.
 */
public final class CommandsConfig {

    private static final String FILE = "commands.yml";
    private static final Pattern WORD = Pattern.compile("[a-z0-9_-]+");

    /** @param paths every path that runs the feature, as words without the slash: {@code [plugin, tag]} */
    public record Feature(String id, boolean enabled, String permission, List<List<String>> paths) {

        public Feature {
            paths = paths.stream().map(List::copyOf).toList();
        }
    }

    private final Map<String, Feature> features;

    private CommandsConfig(Map<String, Feature> features) {
        this.features = Map.copyOf(features);
    }

    public static CommandsConfig load(Plugin plugin, Consumer<String> warn)
            throws IOException, InvalidConfigurationException {
        YamlConfiguration live = ConfigFiles.load(plugin, FILE);
        ConfigValidator.check(plugin, live, FILE, warn);
        YamlConfiguration bundled = ConfigFiles.bundled(plugin, FILE);
        return parse(live, bundled == null ? new MemoryConfiguration() : bundled, warn);
    }

    /** {@code bundled} decides which features exist; {@code live} overrides their values. */
    public static CommandsConfig parse(ConfigurationSection live, ConfigurationSection bundled, Consumer<String> warn) {
        Map<String, Feature> features = new LinkedHashMap<>();
        Map<List<String>, String> taken = new HashMap<>();
        for (String id : bundled.getKeys(false)) {
            ConfigurationSection defaults = bundled.getConfigurationSection(id);
            if (defaults == null) {
                continue;
            }
            ConfigReader reader = new ConfigReader(live, FILE, warn).sectionOrEmpty(id);
            boolean enabled = reader.bool("enabled", defaults.getBoolean("enabled", true));
            String permission = reader.string("permission", defaults.getString("permission", ""));
            List<String> usages = reader.isSet("usage") ? reader.strings("usage") : defaults.getStringList("usage");
            List<List<String>> paths = new ArrayList<>();
            for (String usage : usages) {
                List<String> path = path(usage);
                if (path == null) {
                    reader.warn("usage", "'" + usage + "' must be a slash and lowercase words, like '/plugin tag'");
                } else if (enabled && taken.containsKey(path)) {
                    reader.warn("usage", "'" + usage + "' already runs '" + taken.get(path) + "', ignored");
                } else {
                    if (enabled) {
                        taken.put(path, id);
                    }
                    paths.add(path);
                }
            }
            if (enabled && paths.isEmpty()) {
                reader.warn("usage", "no usable path, so the command can't be run");
            }
            features.put(id, new Feature(id, enabled, permission, paths));
        }
        return new CommandsConfig(features);
    }

    public @Nullable Feature feature(String id) {
        return features.get(id);
    }

    /** The first path of an enabled feature, such as {@code /plugin tag}, for clickable chat links. */
    public @Nullable String primaryUsage(String id) {
        Feature feature = features.get(id);
        if (feature == null || !feature.enabled() || feature.paths().isEmpty()) {
            return null;
        }
        return "/" + String.join(" ", feature.paths().getFirst());
    }

    /**
     * {@link #primaryUsage} when the viewer may run the feature, else {@code null}: a chat link never offers a command
     * that would answer "Unknown command".
     *
     * @param hasPermission usually {@code sender::hasPermission}
     */
    public @Nullable String usageFor(String id, Predicate<String> hasPermission) {
        Feature feature = features.get(id);
        if (feature == null || !(feature.permission().isBlank() || hasPermission.test(feature.permission()))) {
            return null;
        }
        return primaryUsage(id);
    }

    private static @Nullable List<String> path(String usage) {
        String trimmed = usage.trim();
        if (!trimmed.startsWith("/")) {
            return null;
        }
        List<String> words = Arrays.asList(trimmed.substring(1).trim().split("\\s+"));
        return words.stream().allMatch(word -> WORD.matcher(word).matches()) ? words : null;
    }
}
