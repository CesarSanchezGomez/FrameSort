package com.cesarcosmico.framesort.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigValidatorTest {

    private static final String BUNDLED = """
            config-version: 2
            language: 'en_US'
            limits:
              radius: 64
              per-tick: 4
            types:
              example:
                block: STONE
            """;

    private static List<String> compare(String live, Set<String> open) throws Exception {
        YamlConfiguration liveYaml = new YamlConfiguration();
        liveYaml.loadFromString(live);
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.loadFromString(BUNDLED);
        List<String> warnings = new ArrayList<>();
        ConfigValidator.compare(liveYaml, bundled, "config.yml", open, warnings::add);
        return warnings;
    }

    @Test
    void matchingFileGivesNoWarnings() throws Exception {
        assertEquals(List.of(), compare(BUNDLED, Set.of()));
    }

    @Test
    void olderConfigVersionWarns() throws Exception {
        List<String> warnings = compare(BUNDLED.replace("config-version: 2", "config-version: 1"), Set.of());
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("config-version 1, expected 2"));
    }

    @Test
    void missingLeafKeysAreListed() throws Exception {
        List<String> warnings = compare(BUNDLED.replace("  per-tick: 4\n", ""), Set.of());
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("limits.per-tick"));
    }

    @Test
    void onlyTheTopMostUnknownKeyIsReported() throws Exception {
        List<String> warnings = compare(BUNDLED + "typo:\n  nested:\n    deeper: 1\n", Set.of());
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("[typo]"));
    }

    @Test
    void openSectionsAcceptUserDefinedKeys() throws Exception {
        String live = BUNDLED.replace("  example:\n    block: STONE\n", "  custom:\n    block: DIRT\n");
        assertEquals(List.of(), compare(live, Set.of("types")));
    }
}
