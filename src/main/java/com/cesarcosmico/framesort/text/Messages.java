package com.cesarcosmico.framesort.text;

import com.cesarcosmico.framesort.config.ConfigFiles;
import com.cesarcosmico.framesort.config.ConfigReader;
import com.cesarcosmico.framesort.config.ConfigValidator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Player-facing text from {@code lang/<language>.yml}. A key missing there comes from the jar's copy of that language,
 * then from the jar's {@code en_US}; a key missing everywhere is shown as the key and reported once.
 */
public final class Messages {

    private static final String DEFAULT_LANGUAGE = "en_US";
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final YamlConfiguration messages;
    private final TagResolver prefix;
    private final Consumer<String> missingKey;
    // Text may be built off the main thread (PlaceholderAPI, async callbacks).
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    private Messages(YamlConfiguration messages, Consumer<String> missingKey) {
        this.messages = messages;
        this.missingKey = missingKey;
        String prefixText = messages.getString("prefix");
        this.prefix = Placeholder.parsed("prefix", prefixText == null ? "" : prefixText);
    }

    public static Messages load(Plugin plugin, String language, Consumer<String> warn)
            throws IOException, InvalidConfigurationException {
        String path = path(language);
        if (plugin.getResource(path) == null && !new File(plugin.getDataFolder(), path).exists()) {
            warn.accept("Unknown language '" + language + "', using " + DEFAULT_LANGUAGE + ".");
            path = path(DEFAULT_LANGUAGE);
        }
        YamlConfiguration live = ConfigFiles.load(plugin, path);
        ConfigValidator.check(plugin, live, path, warn);
        dropLegacyCodes(live, path, warn);
        return of(live, ConfigFiles.bundled(plugin, path), ConfigFiles.bundled(plugin, path(DEFAULT_LANGUAGE)),
                plugin.getLogger()::warning);
    }

    static Messages of(YamlConfiguration live, @Nullable YamlConfiguration bundled,
                       @Nullable YamlConfiguration fallback, Consumer<String> missingKey) {
        if (bundled != null && fallback != null) {
            bundled.setDefaults(fallback);
        }
        YamlConfiguration defaults = bundled != null ? bundled : fallback;
        if (defaults != null) {
            live.setDefaults(defaults);
        }
        return new Messages(live, missingKey);
    }

    // A text with a § code is invalid like any other value (warned, and the jar's copy used): the message would never
    // be sent, because MiniMessage throws on it.
    static void dropLegacyCodes(YamlConfiguration live, String path, Consumer<String> warn) {
        for (String key : live.getKeys(true)) {
            Object value = live.get(key);
            boolean legacy = value instanceof String text ? ConfigReader.hasLegacyCode(text)
                    : value instanceof List<?> lines && lines.stream().anyMatch(line -> line instanceof String text
                            && ConfigReader.hasLegacyCode(text));
            if (legacy) {
                warn.accept(path + " > " + key + ": uses § codes, which MiniMessage cannot read;"
                        + " write MiniMessage tags. Using the jar's text.");
                live.set(key, null);
            }
        }
    }

    /** A message; a YAML list is one message whose lines are joined with {@code <newline>}. */
    public Component get(String key, TagResolver... resolvers) {
        return MINI_MESSAGE.deserialize(raw(key), TagResolver.resolver(prefix, TagResolver.resolver(resolvers)));
    }

    // The one-argument getters consult the defaults; getString(key, fallback) would skip them.
    private String raw(String key) {
        if (messages.isList(key)) {
            return String.join("<newline>", messages.getStringList(key));
        }
        if (messages.isString(key)) {
            return Objects.requireNonNullElse(messages.getString(key), key);
        }
        if (reported.add(key)) {
            missingKey.accept("Missing message '" + key + "' in every lang file; showing the key instead.");
        }
        return key;
    }

    private static String path(String language) {
        return "lang/" + language + ".yml";
    }
}
