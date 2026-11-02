package com.cesarcosmico.framesort.storage;

import com.cesarcosmico.framesort.config.DatabaseSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

// The insert differs by dialect, so it runs against real MySQL and MariaDB servers: ./gradlew mysqlTest.
@Tag("mysql")
class TrustRepositoryDialectTest {

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BETO = UUID.randomUUID();

    @TempDir
    Path dir;

    @Test
    void mysql() throws Exception {
        check(TestServers.mysql());
    }

    @Test
    void mariadb() throws Exception {
        check(TestServers.mariadb());
    }

    private void check(DatabaseSettings settings) throws Exception {
        TestServers.dropTables(settings, "framesort_trust, framesort_schema_version");
        TrustRepository repository = new TrustRepository(settings.type());
        try (Database database = Database.open(settings, dir.toFile(), "framesort", Runnable::run, null, "db",
                Logger.getLogger("test"))) {
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
