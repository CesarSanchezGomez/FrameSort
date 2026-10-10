package com.cesarcosmico.framesort.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFilesTest {

    @TempDir
    Path dir;

    @Test
    void brokenYamlThrowsWithTheFileName() throws Exception {
        Path file = Files.writeString(dir.resolve("config.yml"), "key: [unclosed\n");
        InvalidConfigurationException e = assertThrows(InvalidConfigurationException.class,
                () -> ConfigFiles.read(file.toFile(), "config.yml"));
        assertTrue(e.getMessage().contains("config.yml"));
    }

    @Test
    void validYamlLoads() throws Exception {
        Path file = Files.writeString(dir.resolve("config.yml"), "language: 'es_ES'\n");
        assertEquals("es_ES", ConfigFiles.read(file.toFile(), "config.yml").getString("language"));
    }

    @Test
    void brokenBundledYamlFailsLoudly() {
        ByteArrayInputStream in = new ByteArrayInputStream("key: [unclosed\n".getBytes(StandardCharsets.UTF_8));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ConfigFiles.readBundled(in, "lang/en_US.yml"));
        assertTrue(e.getMessage().contains("lang/en_US.yml"));
    }
}
