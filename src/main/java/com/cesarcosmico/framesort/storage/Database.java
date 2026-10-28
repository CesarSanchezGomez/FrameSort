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
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * The connection pool, the migrations and the single thread every query runs on. One thread keeps writes in order,
 * so conditional upserts need no locks; callers return to the main thread themselves.
 */
public final class Database implements AutoCloseable {

    private static final String MIGRATIONS = "db";
    private static final int SHUTDOWN_SECONDS = 10;
    private static final long CONNECTION_TIMEOUT_MILLIS = 10_000;

    @FunctionalInterface
    public interface SqlFunction<T extends @Nullable Object> {
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
    private final Logger logger;

    private Database(HikariDataSource dataSource, DatabaseType type, Executor executor,
                     @Nullable ExecutorService ownedExecutor, Logger logger) {
        this.dataSource = dataSource;
        this.type = type;
        this.executor = executor;
        this.ownedExecutor = ownedExecutor;
        this.logger = logger;
    }

    /**
     * Opens the pool and applies pending migrations. Blocks: call it from {@code onEnable}, before players join. The
     * applied versions go in {@code <plugin>_schema_version}, the plugin's name in lower case with anything but a
     * letter, a digit or {@code _} as {@code _}, because servers often give several plugins one MySQL database.
     */
    public static Database open(Plugin plugin, DatabaseSettings settings) throws SQLException {
        String name = tablePrefix(plugin.getName());
        ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, name + "-db");
            thread.setDaemon(true);
            return thread;
        });
        return open(settings, plugin.getDataFolder(), name, executor, executor, MIGRATIONS, plugin.getLogger());
    }

    // Plugin names may hold '-', '.' and spaces, which an unquoted table name can't.
    static String tablePrefix(String pluginName) {
        return pluginName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    static Database open(DatabaseSettings settings, File dataFolder, String name, Executor executor,
                         @Nullable ExecutorService ownedExecutor, String migrations, Logger logger)
            throws SQLException {
        HikariDataSource dataSource = new HikariDataSource(poolConfig(settings, dataFolder, name));
        Database database = new Database(dataSource, settings.type(), executor, ownedExecutor, logger);
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

    public <T extends @Nullable Object> CompletableFuture<T> query(SqlFunction<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection()) {
                return work.apply(connection);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    public CompletableFuture<@Nullable Void> update(SqlConsumer work) {
        return query(connection -> {
            work.accept(connection);
            return null;
        });
    }

    /** Runs {@code work} in one transaction: commit when it returns, rollback on any exception. */
    public <T extends @Nullable Object> CompletableFuture<T> transaction(SqlFunction<T> work) {
        return query(connection -> inTransaction(connection, work));
    }

    /** Waits for queued work, then closes the pool. Work still queued after {@value SHUTDOWN_SECONDS} s is logged. */
    @Override
    public void close() {
        if (ownedExecutor != null) {
            ownedExecutor.shutdown();
            try {
                if (!ownedExecutor.awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
                    dropped(ownedExecutor.shutdownNow().size());
                }
            } catch (InterruptedException e) {
                dropped(ownedExecutor.shutdownNow().size());
                Thread.currentThread().interrupt();
            }
        }
        dataSource.close();
    }

    private void dropped(int tasks) {
        if (tasks > 0) {
            logger.warning(tasks + " database task(s) were still queued at shutdown and did not run.");
        }
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

    private static <T extends @Nullable Object> T inTransaction(Connection connection, SqlFunction<T> work)
            throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        T result;
        try {
            result = work.apply(connection);
            connection.commit();
        } catch (SQLException | RuntimeException | Error e) {
            // On a dead connection the cleanup fails too; the first error is the one that explains it.
            try {
                connection.rollback();
                connection.setAutoCommit(autoCommit);
            } catch (SQLException cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
        connection.setAutoCommit(autoCommit);
        return result;
    }

    // MySQL commits DDL implicitly, so a failing MySQL migration can leave its earlier statements applied.
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

    private static @Nullable List<String> migration(String resource) {
        try (InputStream in = Database.class.getClassLoader().getResourceAsStream(resource)) {
            return in == null ? null : statements(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the migration " + resource, e);
        }
    }

    // Split on ';', so a migration must not have ';' inside a string, a comment or a trigger body.
    static List<String> statements(String sql) {
        List<String> statements = new ArrayList<>();
        for (String piece : withoutComments(sql).split(";")) {
            // A comment after the last ';' of a line starts the next piece: alone, MySQL rejects it as empty.
            String statement = withoutComments(piece).trim();
            if (!statement.isEmpty()) {
                statements.add(statement);
            }
        }
        return statements;
    }

    private static String withoutComments(String sql) {
        return sql.lines().filter(line -> !line.trim().startsWith("--")).collect(Collectors.joining("\n"));
    }
}
