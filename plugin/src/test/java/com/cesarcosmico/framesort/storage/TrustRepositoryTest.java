package com.cesarcosmico.framesort.storage;

import com.cesarcosmico.framesort.config.DatabaseSettings;
import com.cesarcosmico.framesort.config.DatabaseType;
import com.cesarcosmico.framesort.model.TrustList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Real SQLite in a temporary folder, with the plugin's own migrations and a direct executor.
class TrustRepositoryTest {

    private static final UUID ANA = UUID.fromString("00000000-0000-0001-0000-000000000002");
    private static final UUID BETO = UUID.randomUUID();
    private static final UUID CARLA = UUID.randomUUID();

    private final TrustRepository repository = new TrustRepository(DatabaseType.SQLITE);

    @TempDir
    Path dir;

    private Database open() throws SQLException {
        return Database.open(DatabaseSettings.sqlite("data.db"), dir.toFile(), "framesort", Runnable::run, null, "db",
                Logger.getLogger("test"));
    }

    @Test
    void savesAndLoadsTheWholeList() throws Exception {
        try (Database database = open()) {
            database.update(c -> {
                repository.grant(c, ANA, BETO, 10);
                repository.grant(c, ANA, CARLA, 20);
                repository.grant(c, BETO, ANA, 30);
            }).join();
        }
        try (Database database = open()) {
            TrustList list = database.query(repository::load).join();
            assertEquals(Map.of(BETO, 10L, CARLA, 20L), list.trustedBy(ANA));
            assertEquals(Map.of(ANA, 30L), list.trustedBy(BETO));
        }
    }

    @Test
    void trustingTwiceKeepsOneRowAndTheFirstDate() throws Exception {
        try (Database database = open()) {
            database.update(c -> {
                repository.grant(c, ANA, BETO, 10);
                repository.grant(c, ANA, BETO, 99);
            }).join();
            assertEquals(Map.of(BETO, 10L), database.query(repository::load).join().trustedBy(ANA));
        }
    }

    @Test
    void revokingRemovesOnlyThatPair() throws Exception {
        try (Database database = open()) {
            database.update(c -> {
                repository.grant(c, ANA, BETO, 10);
                repository.grant(c, ANA, CARLA, 20);
                repository.revoke(c, ANA, BETO);
                repository.revoke(c, CARLA, ANA);
            }).join();
            assertEquals(Map.of(CARLA, 20L), database.query(repository::load).join().trustedBy(ANA));
        }
    }

    @Test
    void anErrorReachesTheCaller() throws Exception {
        try (Database database = open()) {
            database.update(c -> c.createStatement().execute("DROP TABLE framesort_trust")).join();
            CompletionException e = assertThrows(CompletionException.class,
                    () -> database.query(repository::load).join());
            assertInstanceOf(SQLException.class, e.getCause());
        }
    }

    @Test
    void aUuidIsSixteenBytesMostSignificantFirst() {
        byte[] bytes = TrustRepository.bytes(ANA);

        assertEquals(16, bytes.length);
        assertEquals(1, bytes[7]);
        assertEquals(2, bytes[15]);
        assertEquals(ANA, TrustRepository.uuid(bytes));
    }
}
