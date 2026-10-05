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
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void translationsHaveTheSameKeys() throws Exception {
        assertEquals(leaves(lang("en_US")), leaves(lang("es_ES")));
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
}
