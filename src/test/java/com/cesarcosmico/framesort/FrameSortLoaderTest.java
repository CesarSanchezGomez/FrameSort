package com.cesarcosmico.framesort;

import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameSortLoaderTest {

    // The build compiles and tests against one HikariCP; the server downloads the loader's. They must be the same.
    @Test
    void theServerGetsTheHikariTheBuildUses() throws Exception {
        Properties gradle = new Properties();
        try (Reader in = Files.newBufferedReader(Path.of("gradle.properties"))) {
            gradle.load(in);
        }
        String hikari = "com.zaxxer:HikariCP:" + gradle.getProperty("hikaricp_version");
        assertTrue(FrameSortLoader.LIBRARIES.contains(hikari), FrameSortLoader.LIBRARIES + " lacks " + hikari);
    }

    // The dialect tests run against gradle.properties' MariaDB driver; servers must get the same one.
    @Test
    void theServerGetsTheMariaDbDriverTheDialectTestsUse() throws Exception {
        Properties gradle = new Properties();
        try (Reader in = Files.newBufferedReader(Path.of("gradle.properties"))) {
            gradle.load(in);
        }
        String driver = "org.mariadb.jdbc:mariadb-java-client:" + gradle.getProperty("mariadb_client_version");
        assertTrue(FrameSortLoader.LIBRARIES.contains(driver), FrameSortLoader.LIBRARIES + " lacks " + driver);
    }
}
