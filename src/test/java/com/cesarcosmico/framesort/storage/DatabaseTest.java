package com.cesarcosmico.framesort.storage;

import com.cesarcosmico.framesort.config.DatabaseSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Real SQLite in a temporary folder with a direct executor: no mocks, no server.
class DatabaseTest {

    @TempDir
    Path dir;

    private Database open() throws SQLException {
        return open("test", "test-db");
    }

    private Database open(String name, String migrations) throws SQLException {
        return Database.open(DatabaseSettings.sqlite("data.db"), dir.toFile(), name, Runnable::run, null,
                migrations, Logger.getLogger("test"));
    }

    private static int count(Database database, String sql) {
        return database.query(connection -> {
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
                result.next();
                return result.getInt(1);
            }
        }).join();
    }

    @Test
    void migrationsRunInOrderOnlyOnce() throws Exception {
        try (Database database = open()) {
            assertEquals(2, count(database, "SELECT MAX(version) FROM test_schema_version"));
            database.update(c -> c.createStatement().execute("INSERT INTO items (id, name, amount) VALUES (1, 'a', 5)"))
                    .join();
        }
        try (Database database = open()) {
            assertEquals(2, count(database, "SELECT COUNT(*) FROM test_schema_version"));
            assertEquals(5, count(database, "SELECT amount FROM items WHERE id = 1"));
        }
    }

    @Test
    void pluginsSharingADatabaseKeepTheirOwnVersions() throws Exception {
        try (Database database = open()) {
            assertEquals(2, count(database, "SELECT MAX(version) FROM test_schema_version"));
        }
        try (Database database = open("other", "test-db-other")) {
            assertEquals(1, count(database, "SELECT MAX(version) FROM other_schema_version"));
            assertEquals(0, count(database, "SELECT COUNT(*) FROM notes"));
        }
    }

    @Test
    void sqlErrorsCompleteTheFutureExceptionally() throws Exception {
        try (Database database = open()) {
            CompletableFuture<Integer> future = database.query(c -> {
                c.createStatement().executeQuery("SELECT * FROM missing_table");
                return 0;
            });
            CompletionException e = assertThrows(CompletionException.class, future::join);
            assertInstanceOf(SQLException.class, e.getCause());
        }
    }

    @Test
    void transactionRollsBackOnAnyException() throws Exception {
        try (Database database = open()) {
            CompletableFuture<Void> future = database.transaction(c -> {
                try (PreparedStatement insert = c.prepareStatement("INSERT INTO items (id, name) VALUES (?, ?)")) {
                    insert.setInt(1, 7);
                    insert.setString(2, "b");
                    insert.executeUpdate();
                }
                throw new IllegalStateException("not an SQLException");
            });
            assertThrows(CompletionException.class, future::join);
            assertEquals(0, count(database, "SELECT COUNT(*) FROM items"));
        }
    }

    @Test
    void aCommentAfterAStatementIsNotAStatement() {
        assertEquals(List.of("CREATE TABLE a (id INTEGER)", "CREATE TABLE b (id INTEGER)"), Database.statements("""
                -- V1: two tables; one comment line
                CREATE TABLE a (id INTEGER); -- players
                CREATE TABLE b (id INTEGER); -- notes
                -- the end
                """));
    }

    @Test
    void tablePrefixesAreValidSqlNames() {
        assertEquals("myplugin", Database.tablePrefix("MyPlugin"));
        assertEquals("sorter_x_2", Database.tablePrefix("Sorter-X.2"));
        assertEquals("my_plugin", Database.tablePrefix("My Plugin"));
    }

    @Test
    void foreignKeysAreEnforced() throws Exception {
        try (Database database = open()) {
            CompletableFuture<Void> future = database.update(c ->
                    c.createStatement().execute("INSERT INTO tags (item_id, tag) VALUES (99, 'orphan')"));
            assertThrows(CompletionException.class, future::join);
        }
    }
}
