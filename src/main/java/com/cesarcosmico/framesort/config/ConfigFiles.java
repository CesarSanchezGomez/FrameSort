package com.cesarcosmico.framesort.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Loads the YAML files in the data folder strictly. {@code YamlConfiguration.loadConfiguration} and
 * {@code reloadConfig} read a file with a syntax error as an empty one, so a reload would silently apply every
 * default; here it throws and the caller keeps the previous configuration.
 */
public final class ConfigFiles {

    private ConfigFiles() {
    }

    /** The file in the data folder, written from the jar first when it does not exist. */
    public static YamlConfiguration load(Plugin plugin, String path) throws IOException, InvalidConfigurationException {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists() && plugin.getResource(path) != null) {
            plugin.saveResource(path, false);
        }
        return read(file, path);
    }

    static YamlConfiguration read(File file, String path) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (InvalidConfigurationException e) {
            throw new InvalidConfigurationException(path + " is not valid YAML", e);
        }
        return yaml;
    }

    /** The copy bundled in the jar, or {@code null} when the jar has none. */
    public static @Nullable YamlConfiguration bundled(Plugin plugin, String path) {
        try (InputStream in = plugin.getResource(path)) {
            return in == null ? null : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read the bundled " + path, e);
            return null;
        }
    }
}
