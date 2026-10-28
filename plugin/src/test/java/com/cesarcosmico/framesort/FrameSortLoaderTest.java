package com.cesarcosmico.framesort;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameSortLoaderTest {

    private static String catalogVersion(String name) throws Exception {
        String catalog = Files.readString(Path.of("../gradle/libs.versions.toml"));
        Matcher version = Pattern.compile("(?m)^" + Pattern.quote(name) + " = \"([^\"]+)\"").matcher(catalog);
        assertTrue(version.find(), "gradle/libs.versions.toml has no " + name + " version");
        return version.group(1);
    }

    // The build compiles and tests against one HikariCP; the server downloads the loader's. They must be the same.
    @Test
    void theServerGetsTheHikariTheBuildUses() throws Exception {
        String hikari = "com.zaxxer:HikariCP:" + catalogVersion("hikaricp");
        assertTrue(FrameSortLoader.LIBRARIES.contains(hikari), FrameSortLoader.LIBRARIES + " lacks " + hikari);
    }

    // The dialect tests run against the catalog's MariaDB driver; servers must get the same one.
    @Test
    void theServerGetsTheMariaDbDriverTheDialectTestsUse() throws Exception {
        String driver = "org.mariadb.jdbc:mariadb-java-client:" + catalogVersion("mariadb-java-client");
        assertTrue(FrameSortLoader.LIBRARIES.contains(driver), FrameSortLoader.LIBRARIES + " lacks " + driver);
    }
}
