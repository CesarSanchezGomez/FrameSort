package com.cesarcosmico.framesort.text;

import com.cesarcosmico.framesort.config.ConfigFiles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/** Player-facing text from {@code lang/<language>.yml}; keys missing there fall back to the bundled file. */
public final class Messages {

    private static final String DEFAULT_LANGUAGE = "en_US";
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final YamlConfiguration messages;
    private final TagResolver prefix;

    private Messages(YamlConfiguration messages) {
        this.messages = messages;
        this.prefix = Placeholder.parsed("prefix", messages.getString("prefix", ""));
    }

    public static Messages load(Plugin plugin, String language, Consumer<String> warn)
            throws IOException, InvalidConfigurationException {
        String path = "lang/" + language + ".yml";
        if (plugin.getResource(path) == null && !new File(plugin.getDataFolder(), path).exists()) {
            warn.accept("Unknown language '" + language + "', using " + DEFAULT_LANGUAGE + ".");
            path = "lang/" + DEFAULT_LANGUAGE + ".yml";
        }
        YamlConfiguration loaded = ConfigFiles.load(plugin, path);
        YamlConfiguration defaults = ConfigFiles.bundled(plugin,
                plugin.getResource(path) != null ? path : "lang/" + DEFAULT_LANGUAGE + ".yml");
        if (defaults != null) {
            loaded.setDefaults(defaults);
        }
        return new Messages(loaded);
    }

    /** A message; a YAML list is one message whose lines are joined with {@code <newline>}. */
    public Component get(String key, TagResolver... resolvers) {
        String raw = messages.isList(key)
                ? String.join("<newline>", messages.getStringList(key))
                : messages.getString(key, key);
        return MINI_MESSAGE.deserialize(raw,
                TagResolver.resolver(prefix, TagResolver.resolver(resolvers)));
    }
}
