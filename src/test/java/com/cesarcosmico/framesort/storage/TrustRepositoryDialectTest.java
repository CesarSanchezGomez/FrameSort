package com.cesarcosmico.framesort.storage;

import com.cesarcosmico.framesort.config.DatabaseSettings;
import com.cesarcosmico.framesort.config.DatabaseType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mariadb.MariaDBContainer;
import org.testcontainers.mysql.MySQLContainer;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

// The insert differs by dialect, so it runs against real MySQL and MariaDB servers: ./gradlew mysqlTest, with Docker.
@Tag("mysql")
class TrustRepositoryDialectTest {

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BETO = UUID.randomUUID();

    @TempDir
    Path dir;

    @Test
    void mysql() throws Exception {
        try (MySQLContainer server = new MySQLContainer("mysql:8.4")) {
            server.start();
            // MySQL 8 signs in with caching_sha2_password, which needs the server's key without TLS.
            check(server, DatabaseType.MYSQL, "useSSL=false&allowPublicKeyRetrieval=true");
        }
    }

    @Test
    void mariadb() throws Exception {
        try (MariaDBContainer server = new MariaDBContainer("mariadb:11.4")) {
            server.start();
            check(server, DatabaseType.MARIADB, "");
        }
    }

    private void check(JdbcDatabaseContainer<?> server, DatabaseType type, String properties) throws Exception {
        DatabaseSettings settings = new DatabaseSettings(type, "unused.db", new DatabaseSettings.Remote(
                server.getHost(), server.getMappedPort(3306), server.getDatabaseName(), server.getUsername(),
                server.getPassword(), properties));
        TrustRepository repository = new TrustRepository(type);
        try (Database database = Database.open(settings, dir.toFile(), "framesort", Runnable::run, null, "db")) {
            database.update(c -> {
                repository.grant(c, ANA, BETO, 10);
                repository.grant(c, ANA, BETO, 99);
            }).join();
            assertEquals(Map.of(BETO, 10L), database.query(repository::load).join().trustedBy(ANA));
            database.update(c -> repository.revoke(c, ANA, BETO)).join();
            assertEquals(Map.of(), database.query(repository::load).join().trustedBy(ANA));
        }
    }
}
