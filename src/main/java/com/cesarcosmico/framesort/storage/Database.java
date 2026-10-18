package com.cesarcosmico.framesort.storage;

import com.cesarcosmico.framesort.config.DatabaseSettings;
import com.cesarcosmico.framesort.config.DatabaseType;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The connection pool, the migrations and the single thread every query runs on. One thread keeps writes in order,
 * so conditional upserts need no locks; callers return to the main thread themselves.
 */
public final class Database implements AutoCloseable {

    private static final String MIGRATIONS = "db";
    private static final long SHUTDOWN_SECONDS = 10;
    private static final long CONNECTION_TIMEOUT_MILLIS = 10_000;

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }

    private final HikariDataSource dataSource;
    private final DatabaseType type;
    private final Executor executor;
    private final @Nullable ExecutorService ownedExecutor;

    private Database(HikariDataSource dataSource, DatabaseType type, Executor executor,
                     @Nullable ExecutorService ownedExecutor) {
        this.dataSource = dataSource;
        this.type = type;
        this.executor = executor;
        this.ownedExecutor = ownedExecutor;
    }

    /**
     * Opens the pool and applies pending migrations. Blocks: call it from {@code onEnable}, before players join. The
     * applied versions go in {@code <plugin>_schema_version}, the plugin's name in lower case.
     */
    public static Database open(Plugin plugin, DatabaseSettings settings) throws SQLException {
        String name = plugin.getName().toLowerCase(Locale.ROOT);
        ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, name + "-db");
            thread.setDaemon(true);
            return thread;
        });
        return open(settings, plugin.getDataFolder(), name, executor, executor, MIGRATIONS);
    }

    static Database open(DatabaseSettings settings, File dataFolder, String name, Executor executor,
                         @Nullable ExecutorService ownedExecutor, String migrations) throws SQLException {
        HikariDataSource dataSource = new HikariDataSource(poolConfig(settings, dataFolder, name));
        Database database = new Database(dataSource, settings.type(), executor, ownedExecutor);
        try {
            database.migrate(migrations, name + "_schema_version");
        } catch (SQLException | RuntimeException e) {
            database.close();
            throw e;
        }
        return database;
    }

    public DatabaseType type() {
        return type;
    }

    public <T> CompletableFuture<T> query(SqlFunction<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return work.apply(connection);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    public CompletableFuture<Void> update(SqlConsumer work) {
        return query(connection -> {
            work.accept(connection);
            return null;
        });
    }

    /** Runs {@code work} in one transaction: commit when it returns, rollback on any exception. */
    public <T> CompletableFuture<T> transaction(SqlFunction<T> work) {
        return query(connection -> inTransaction(connection, work));
    }

    /** Waits for queued work, then closes the pool. */
    @Override
    public void close() {
        if (ownedExecutor != null) {
            ownedExecutor.shutdown();
            try {
                if (!ownedExecutor.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
                    ownedExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                ownedExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        dataSource.close();
    }

    private static HikariConfig poolConfig(DatabaseSettings settings, File dataFolder, String name) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(name + "-pool");
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MILLIS);
        if (settings.type() == DatabaseType.SQLITE) {
            File file = new File(dataFolder, settings.sqliteFile());
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            // The server ships this driver. SQLite starts every connection with foreign keys off.
            config.setDriverClassName("org.sqlite.JDBC");
            config.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            config.addDataSourceProperty("foreign_keys", "true");
            config.setMaximumPoolSize(1);
            return config;
        }
        DatabaseSettings.Remote remote = settings.remote();
        boolean mariadb = settings.type() == DatabaseType.MARIADB;
        String properties = remote.properties().isBlank() ? "" : "?" + remote.properties().trim();
        config.setDriverClassName(mariadb ? "org.mariadb.jdbc.Driver" : "com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl("jdbc:" + (mariadb ? "mariadb" : "mysql") + "://" + remote.host() + ":" + remote.port()
                + "/" + remote.database() + properties);
        config.setUsername(remote.username());
        config.setPassword(remote.password());
        // Every query runs on one thread, so a second connection only covers a reconnect.
        config.setMaximumPoolSize(2);
        return config;
    }

    private static <T> T inTransaction(Connection connection, SqlFunction<T> work) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            T result = work.apply(connection);
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException | Error e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    // Applies <root>/<dialect>/V1.sql, V2.sql… in order, each in its own transaction, until the next file is missing.
    // MySQL commits DDL implicitly, so a failing MySQL migration can leave its earlier statements applied. The versions
    // table carries the plugin's name because servers often give several plugins one MySQL database.
    private void migrate(String root, String versions) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS " + versions + " (version INTEGER PRIMARY KEY)");
            }
            for (int version = currentVersion(connection, versions) + 1; ; version++) {
                List<String> statements = migration(root + "/" + type.dialect() + "/V" + version + ".sql");
                if (statements == null) {
                    return;
                }
                int applied = version;
                inTransaction(connection, c -> {
                    try (Statement statement = c.createStatement()) {
                        for (String sql : statements) {
                            statement.execute(sql);
                        }
                    }
                    try (PreparedStatement insert = c.prepareStatement(
                            "INSERT INTO " + versions + " (version) VALUES (?)")) {
                        insert.setInt(1, applied);
                        insert.executeUpdate();
                    }
                    return null;
                });
            }
        }
    }

    private static int currentVersion(Connection connection, String versions) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT MAX(version) FROM " + versions)) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    // Statements are split on ';', so a migration must not have ';' inside a string or a trigger body.
    private static @Nullable List<String> migration(String resource) {
        try (InputStream in = Database.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            StringBuilder sql = new StringBuilder();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.trim().startsWith("--")) {
                    sql.append(line).append('\n');
                }
            }
            List<String> statements = new ArrayList<>();
            for (String statement : sql.toString().split(";")) {
                if (!statement.isBlank()) {
                    statements.add(statement.trim());
                }
            }
            return statements;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the migration " + resource, e);
        }
    }
}
