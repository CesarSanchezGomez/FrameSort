package com.cesarcosmico.framesort;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

// The plugin reads its bundled YAML strictly at runtime, so a broken file must fail the build instead.
class BundledYamlTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    private static List<Path> yamlFiles() throws IOException {
        try (Stream<Path> files = Files.walk(RESOURCES)) {
            return files.filter(file -> file.toString().endsWith(".yml")).toList();
        }
    }

    @Test
    void everyBundledYamlParsesStrictly() throws Exception {
        List<Path> files = yamlFiles();
        assertFalse(files.isEmpty());
        for (Path file : files) {
            new YamlConfiguration().load(file.toFile());
        }
    }

    @Test
    void everyLanguageHasTheSameKeys() throws Exception {
        Set<String> english = keys(RESOURCES.resolve("lang/en_US.yml"));
        try (Stream<Path> files = Files.list(RESOURCES.resolve("lang"))) {
            for (Path file : files.toList()) {
                assertEquals(english, keys(file), file.getFileName() + " keys differ from en_US.yml");
            }
        }
    }

    private static Set<String> keys(Path file) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file.toFile());
        return yaml.getKeys(true);
    }
}
