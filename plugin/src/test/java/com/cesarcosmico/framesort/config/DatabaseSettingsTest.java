package com.cesarcosmico.framesort.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseSettingsTest {

    private static DatabaseSettings parse(String text, List<String> warnings) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return DatabaseSettings.parse(new ConfigReader(yaml, "database.yml", warnings::add));
    }

    @Test
    void sqliteIsTheDefault() throws Exception {
        DatabaseSettings settings = parse("", new ArrayList<>());
        assertEquals(DatabaseType.SQLITE, settings.type());
        assertEquals("data.db", settings.sqliteFile());
    }

    @Test
    void mysqlAndMariadbShareTheMigrations() {
        assertEquals("mysql", DatabaseType.MYSQL.dialect());
        assertEquals("mysql", DatabaseType.MARIADB.dialect());
        assertEquals("sqlite", DatabaseType.SQLITE.dialect());
    }

    @Test
    void anUnknownTypeStopsInsteadOfFallingBackToSqlite() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse("type: 'mysqll'\n", new ArrayList<>()));
        assertEquals("database.yml > type: unknown 'mysqll' (expected sqlite, mysql or mariadb)", e.getMessage());
    }

    @Test
    void anInvalidPortWarnsAndUsesTheDefault() throws Exception {
        List<String> warnings = new ArrayList<>();
        DatabaseSettings settings = parse("type: 'mariadb'\nremote:\n  port: 70000\n", warnings);
        assertEquals(3306, settings.remote().port());
        assertEquals(1, warnings.size());
    }
}
