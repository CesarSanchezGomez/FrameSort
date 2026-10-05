package com.cesarcosmico.framesort.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code commands.yml} overlaid on each command's defaults. Commands register once, so edits need a restart. */
public final class CommandsConfig {

    private final ConfigurationSection root;

    private CommandsConfig(ConfigurationSection root) {
        this.root = root;
    }

    public static CommandsConfig load(Plugin plugin) {
        String path = "commands.yml";
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            plugin.saveResource(path, false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigValidator.check(plugin, yaml, path);
        return new CommandsConfig(yaml);
    }

    public CommandSpec effective(String id, CommandSpec defaults) {
        ConfigurationSection section = root.getConfigurationSection(id);
        if (section == null) {
            return defaults;
        }
        boolean enabled = section.getBoolean("enabled", defaults.enabled());
        String name = section.getString("name", defaults.name());
        List<String> aliases = section.isSet("aliases") ? section.getStringList("aliases") : defaults.aliases();
        String permission = section.getString("permission", defaults.permission());
        Map<String, String> subPermissions = resolveSubPermissions(
                section.getConfigurationSection("subcommands"), defaults.subPermissions());
        return new CommandSpec(enabled, name, aliases, permission, subPermissions);
    }

    private static Map<String, String> resolveSubPermissions(@Nullable ConfigurationSection section,
                                                             Map<String, String> defaults) {
        if (section == null || defaults.isEmpty()) {
            return defaults;
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        defaults.forEach((sub, defaultPermission) -> {
            ConfigurationSection subSection = section.getConfigurationSection(sub);
            resolved.put(sub, subSection == null ? defaultPermission
                    : subSection.getString("permission", defaultPermission));
        });
        return resolved;
    }
}
