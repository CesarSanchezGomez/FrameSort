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
import java.util.regex.Pattern;

/**
 * {@code commands.yml}: each top-level section is a feature, keyed by a fixed id, with every path that runs it.
 * The bundled file supplies whatever the server's file leaves out. Commands register once, so edits need a restart.
 */
public final class CommandsConfig {

    private static final String FILE = "commands.yml";
    private static final Pattern WORD = Pattern.compile("[a-z0-9_-]+");

    private final Map<String, CommandSpec> specs;

    private CommandsConfig(Map<String, CommandSpec> specs) {
        this.specs = Map.copyOf(specs);
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
        Map<String, CommandSpec> specs = new LinkedHashMap<>();
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
                    reader.warn("usage", "'" + usage + "' must be a slash and lowercase words, like '/framesort tag'");
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
            specs.put(id, new CommandSpec(id, enabled, permission, paths));
        }
        return new CommandsConfig(specs);
    }

    public @Nullable CommandSpec spec(String id) {
        return specs.get(id);
    }

    /** The first path of an enabled feature, such as {@code /framesort tag}, for clickable chat links. */
    public @Nullable String primaryUsage(String id) {
        CommandSpec spec = specs.get(id);
        if (spec == null || !spec.enabled() || spec.paths().isEmpty()) {
            return null;
        }
        return "/" + String.join(" ", spec.paths().getFirst());
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
