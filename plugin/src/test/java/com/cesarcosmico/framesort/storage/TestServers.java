package com.cesarcosmico.framesort.storage;

import com.cesarcosmico.framesort.config.DatabaseSettings;
import com.cesarcosmico.framesort.config.DatabaseType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// The developer's own MySQL and MariaDB, from testServer.mysql.* and testServer.mariadb.* in
// ~/.gradle/gradle.properties (host, port, database, user, password). A test whose server has no host is skipped.
final class TestServers {

    private TestServers() {
    }

    static DatabaseSettings mysql() {
        // MySQL signs in with caching_sha2_password, which needs the server's key without TLS.
        return settings("mysql", DatabaseType.MYSQL, "useSSL=false&allowPublicKeyRetrieval=true");
    }

    static DatabaseSettings mariadb() {
        return settings("mariadb", DatabaseType.MARIADB, "");
    }

    private static DatabaseSettings settings(String server, DatabaseType type, String properties) {
        String prefix = "testServer." + server + ".";
        String host = System.getProperty(prefix + "host");
        assumeTrue(host != null, "No " + prefix + "* in ~/.gradle/gradle.properties");
        String database = System.getProperty(prefix + "database");
        // These tests drop their tables, so a real database must not be reachable by a typo.
        assertTrue(database != null && database.matches("(.*_)?test(_.*)?"),
                prefix + "database must be only for tests, with test as a word in its name");
        return new DatabaseSettings(type, "unused.db", new DatabaseSettings.Remote(host,
                Integer.parseInt(System.getProperty(prefix + "port")), database,
                System.getProperty(prefix + "user"), System.getProperty(prefix + "password"), properties));
    }

    static Connection connect(DatabaseSettings settings) throws SQLException {
        DatabaseSettings.Remote remote = settings.remote();
        String properties = remote.properties().isBlank() ? "" : "?" + remote.properties();
        return DriverManager.getConnection("jdbc:" + settings.type().name().toLowerCase(Locale.ROOT) + "://"
                + remote.host() + ":" + remote.port() + "/" + remote.database() + properties,
                remote.username(), remote.password());
    }

    // The server stays between runs, so a test starts by dropping what it creates.
    static void dropTables(DatabaseSettings settings, String tables) throws SQLException {
        try (Connection connection = connect(settings); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + tables);
        }
    }
}
