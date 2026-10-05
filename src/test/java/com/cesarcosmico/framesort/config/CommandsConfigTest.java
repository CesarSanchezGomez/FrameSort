package com.cesarcosmico.framesort.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

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
    void bundledCommandsParseWithoutWarnings() throws Exception {
        YamlConfiguration bundled = FrameSortSettingsTest.bundled("commands.yml");
        List<String> warnings = new ArrayList<>();
        CommandsConfig config = CommandsConfig.parse(bundled, bundled, warnings::add);

        assertEquals(List.of(), warnings);
        CommandSpec tag = config.spec("tag");
        assertNotNull(tag);
        assertEquals("framesort.command.tag", tag.permission());
        assertEquals(List.of(List.of("framesort", "tag"), List.of("fs", "tag")), tag.paths());
        assertEquals("/framesort inspect", config.primaryUsage("inspect"));
    }

    @Test
    void missingValuesComeFromTheBundledFile() throws Exception {
        YamlConfiguration live = yaml("""
                tag:
                  usage:
                    - '/fstag'
                """);
        CommandsConfig config = CommandsConfig.parse(live, FrameSortSettingsTest.bundled("commands.yml"), w -> { });

        CommandSpec tag = config.spec("tag");
        assertNotNull(tag);
        assertEquals(List.of(List.of("fstag")), tag.paths());
        assertEquals("framesort.command.tag", tag.permission());
        assertEquals("/framesort reload", config.primaryUsage("reload"));
    }

    @Test
    void invalidAndDuplicatePathsAreSkipped() throws Exception {
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

        assertEquals(List.of(List.of("fs", "tag")), config.spec("tag").paths());
        assertEquals(List.of(List.of("fs", "tags")), config.spec("tags").paths());
        assertEquals(3, warnings.size(), warnings.toString());
    }

    @Test
    void disabledFeaturesHaveNoUsage() throws Exception {
        YamlConfiguration bundled = yaml("""
                trace:
                  permission: 'p'
                  usage: ['/fs trace']
                """);
        CommandsConfig config = CommandsConfig.parse(yaml("trace: {enabled: false}"), bundled, w -> { });

        assertFalse(config.spec("trace").enabled());
        assertNull(config.primaryUsage("trace"));
    }
}
