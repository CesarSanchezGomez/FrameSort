package com.cesarcosmico.framesort.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigFilesTest {

    @Test
    void brokenYamlThrowsInsteadOfReadingAsEmpty(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, "language: 'en_US'\nsorter:\n  radius: [1, 2\n");

        assertThrows(InvalidConfigurationException.class, () -> ConfigFiles.read(file.toFile(), "config.yml"));
    }

    @Test
    void validYamlLoads(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, "language: 'es_ES'\n");

        assertEquals("es_ES", ConfigFiles.read(file.toFile(), "config.yml").getString("language"));
    }
}
