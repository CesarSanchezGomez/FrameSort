package com.cesarcosmico.framesort.text;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

// BundledYamlTest checks that every language has the same keys; this checks that every message parses.
class LangFilesTest {

    private static YamlConfiguration lang(String locale) throws Exception {
        try (InputStream in = LangFilesTest.class.getResourceAsStream("/lang/" + locale + ".yml")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static Set<String> leaves(YamlConfiguration yaml) {
        Set<String> keys = new TreeSet<>();
        for (String key : yaml.getKeys(true)) {
            if (!yaml.isConfigurationSection(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    @Test
    void everyMessageParses() throws Exception {
        MiniMessage strict = MiniMessage.builder().strict(true).build();
        for (String locale : new String[]{"en_US", "es_ES"}) {
            YamlConfiguration yaml = lang(locale);
            for (String key : leaves(yaml)) {
                String value = yaml.isList(key)
                        ? String.join("<newline>", yaml.getStringList(key))
                        : yaml.getString(key, "");
                assertDoesNotThrow(() -> strict.deserialize(value), locale + " " + key);
            }
        }
    }

    // YAML 1.1 reads a key named on, off, yes or no as a boolean, so the message ends up under true or false and the
    // code's key finds nothing.
    @Test
    void noKeyIsReadAsABoolean() throws Exception {
        for (String locale : new String[]{"en_US", "es_ES"}) {
            for (String key : lang(locale).getKeys(true)) {
                String last = key.substring(key.lastIndexOf('.') + 1);
                assertFalse(last.equals("true") || last.equals("false"), locale + " " + key);
            }
        }
    }
}
