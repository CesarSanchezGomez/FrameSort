package com.cesarcosmico.framesort.storage;

import com.cesarcosmico.framesort.config.DatabaseSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The migration lock only exists on a shared server, so it runs against real MySQL and MariaDB: ./gradlew mysqlTest.
@Tag("mysql")
class DatabaseLockTest {

    private static final String MIGRATIONS = "test-db-lock";
    private static final String BROKEN_MIGRATIONS = "test-db-lock-broken";
    // At most the 64 of a table name once _schema_version is added, and over MySQL's 64 once the database name is.
    private static final String LONG_NAME = "a".repeat(49);
    private static final Duration AT_ONCE = Duration.ofSeconds(10);
    private static final String LOCK_NAME = "LEFT(CONCAT(DATABASE(), '.', ?), 64)";

    @Test
    void mysql() throws Exception {
        allCases(TestServers.mysql());
    }

    @Test
    void mariadb() throws Exception {
        allCases(TestServers.mariadb());
    }

    private static void allCases(DatabaseSettings settings) throws Exception {
        twoAtOnceApplyAMigrationOnce(settings);
        aFailedMigrationLeavesTheLockFree(settings);
        aLockHeldTooLongStopsTheStart(settings);
        nothingToApplyAsksForNoLock(settings);
        anotherPluginsLockIsNotInTheWay(settings);
        aLongNameFitsTheLock(settings);
    }

    private static void twoAtOnceApplyAMigrationOnce(DatabaseSettings settings) throws Exception {
        clean(settings, "twice");
        try (ExecutorService servers = Executors.newFixedThreadPool(2)) {
            CompletableFuture<Database> first = openAsync(settings, servers);
            CompletableFuture<Database> second = openAsync(settings, servers);
            try (Database one = first.join(); Database other = second.join()) {
                assertEquals(1, count(one, "SELECT COUNT(*) FROM twice_schema_version"));
                assertEquals(1, count(other, "SELECT MAX(version) FROM twice_schema_version"));
            }
        }
    }

    private static CompletableFuture<Database> openAsync(DatabaseSettings settings, ExecutorService servers) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return open(settings, "twice", MIGRATIONS);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, servers);
    }

    private static void aFailedMigrationLeavesTheLockFree(DatabaseSettings settings) throws Exception {
        clean(settings, "broken");
        assertThrows(SQLException.class, () -> open(settings, "broken", BROKEN_MIGRATIONS));
        try (Connection connection = TestServers.connect(settings)) {
            // The failed server's session ends just after its pool closes, so this waits a moment for it.
            assertEquals(1, getLock(connection, "broken", 5));
            release(connection, "broken");
        }
    }

    private static void aLockHeldTooLongStopsTheStart(DatabaseSettings settings) throws Exception {
        clean(settings, "held");
        try (Connection holder = TestServers.connect(settings)) {
            assertEquals(1, getLock(holder, "held", 0));
            SQLException failure = assertThrows(SQLException.class, () -> open(settings, "held", MIGRATIONS));
            assertTrue(failure.getMessage().contains("Another server"), failure.getMessage());
            assertEquals(0, tablesNamed(holder, "lock_test_first"));
        }
    }

    private static void nothingToApplyAsksForNoLock(DatabaseSettings settings) throws Exception {
        clean(settings, "ready");
        open(settings, "ready", MIGRATIONS).close();
        try (Connection holder = TestServers.connect(settings)) {
            assertEquals(1, getLock(holder, "ready", 0));
            assertTimeout(AT_ONCE, () -> open(settings, "ready", MIGRATIONS).close());
        }
    }

    private static void anotherPluginsLockIsNotInTheWay(DatabaseSettings settings) throws Exception {
        clean(settings, "mine");
        try (Connection holder = TestServers.connect(settings)) {
            assertEquals(1, getLock(holder, "other", 0));
            assertTimeout(AT_ONCE, () -> open(settings, "mine", MIGRATIONS).close());
        }
    }

    private static void aLongNameFitsTheLock(DatabaseSettings settings) throws Exception {
        clean(settings, LONG_NAME);
        open(settings, LONG_NAME, MIGRATIONS).close();
    }

    private static Database open(DatabaseSettings settings, String name, String migrations) throws SQLException {
        return Database.open(settings, new File("unused"), name, Runnable::run, null, migrations,
                Logger.getLogger("test"));
    }

    private static void clean(DatabaseSettings settings, String name) throws SQLException {
        TestServers.dropTables(settings, "lock_test_first, lock_test_second, " + name + "_schema_version");
    }

    private static int getLock(Connection connection, String name, int seconds) throws SQLException {
        try (PreparedStatement lock = connection.prepareStatement("SELECT GET_LOCK(" + LOCK_NAME + ", ?)")) {
            lock.setString(1, name + "_schema_version");
            lock.setInt(2, seconds);
            try (ResultSet result = lock.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static void release(Connection connection, String name) throws SQLException {
        try (PreparedStatement release = connection.prepareStatement("SELECT RELEASE_LOCK(" + LOCK_NAME + ")")) {
            release.setString(1, name + "_schema_version");
            release.executeQuery().close();
        }
    }

    private static int tablesNamed(Connection connection, String table) throws SQLException {
        try (PreparedStatement count = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?")) {
            count.setString(1, table);
            try (ResultSet result = count.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static int count(Database database, String sql) {
        return database.query(connection -> {
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
                result.next();
                return result.getInt(1);
            }
        }).join();
    }
}
