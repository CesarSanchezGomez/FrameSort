package com.cesarcosmico.framesort.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommandsConfigTest {

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    @Test
    void bundledTemplateParsesWithoutWarnings() throws Exception {
        YamlConfiguration bundled = new YamlConfiguration();
        try (InputStream in = CommandsConfigTest.class.getResourceAsStream("/commands.yml")) {
            assertNotNull(in);
            bundled.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        List<String> warnings = new ArrayList<>();
        CommandsConfig config = CommandsConfig.parse(bundled, bundled, warnings::add);

        assertEquals(List.of(), warnings);
        assertEquals("/framesort", config.primaryUsage("help"));
        assertEquals("/framesort reload", config.primaryUsage("reload"));
    }

    @Test
    void missingValuesComeFromTheBundledFile() throws Exception {
        YamlConfiguration bundled = yaml("tag:\n  permission: 'p.tag'\n  usage: ['/p tag']\n");
        CommandsConfig config = CommandsConfig.parse(yaml("tag:\n  usage: ['/ptag']\n"), bundled, warning -> { });

        CommandsConfig.Feature tag = config.feature("tag");
        assertNotNull(tag);
        assertEquals(List.of(List.of("ptag")), tag.paths());
        assertEquals("p.tag", tag.permission());
    }

    @Test
    void invalidAndDuplicatePathsAreSkippedWithAWarning() throws Exception {
        YamlConfiguration bundled = yaml("""
                tag:
                  permission: 'a'
                  usage: ['/fs tag']
                tags:
                  permission: 'b'
                  usage: ['/fs tags']
                """);
        YamlConfiguration live = yaml("""
                tag:
                  usage: ['fs tag', '/fs Tag', '/fs tag']
                tags:
                  usage: ['/fs tag', '/fs tags']
                """);
        List<String> warnings = new ArrayList<>();
        CommandsConfig config = CommandsConfig.parse(live, bundled, warnings::add);

        assertEquals(List.of(List.of("fs", "tag")), config.feature("tag").paths());
        assertEquals(List.of(List.of("fs", "tags")), config.feature("tags").paths());
        assertEquals(3, warnings.size(), warnings.toString());
    }

    @Test
    void disabledFeaturesHaveNoUsage() throws Exception {
        YamlConfiguration bundled = yaml("trace:\n  permission: 'p'\n  usage: ['/fs trace']\n");
        CommandsConfig config = CommandsConfig.parse(yaml("trace: {enabled: false}"), bundled, warning -> { });

        assertFalse(config.feature("trace").enabled());
        assertNull(config.primaryUsage("trace"));
    }

    @Test
    void featuresTheJarDoesNotHaveAreIgnored() throws Exception {
        YamlConfiguration bundled = yaml("help:\n  usage: ['/p']\n");
        CommandsConfig config = CommandsConfig.parse(yaml("made-up:\n  usage: ['/x']\n"), bundled, warning -> { });
        assertNull(config.feature("made-up"));
    }
}
