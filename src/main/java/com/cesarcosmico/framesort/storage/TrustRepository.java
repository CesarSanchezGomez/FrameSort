package com.cesarcosmico.framesort.storage;

import com.cesarcosmico.framesort.config.DatabaseType;
import com.cesarcosmico.framesort.model.TrustList;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The SQL of the trust table. Each call takes the connection of a task already on the database thread, so whoever
 * calls it can change the in-memory list in that same task, in the order the database saw.
 */
public final class TrustRepository {

    private static final String LOAD = "SELECT owner, trusted, created_at FROM framesort_trust";
    private static final String INSERT = "INSERT INTO framesort_trust (owner, trusted, created_at) VALUES (?, ?, ?)";
    // INSERT IGNORE would also turn conversion errors into warnings; these only skip the row that exists.
    private static final String GRANT_SQLITE = INSERT + " ON CONFLICT (owner, trusted) DO NOTHING";
    private static final String GRANT_MYSQL = INSERT + " ON DUPLICATE KEY UPDATE owner = owner";
    private static final String REVOKE = "DELETE FROM framesort_trust WHERE owner = ? AND trusted = ?";

    private final String grant;

    public TrustRepository(DatabaseType type) {
        this.grant = type == DatabaseType.SQLITE ? GRANT_SQLITE : GRANT_MYSQL;
    }

    public TrustList load(Connection connection) throws SQLException {
        Map<UUID, Map<UUID, Long>> grants = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(LOAD);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                grants.computeIfAbsent(uuid(rows.getBytes(1)), owner -> new HashMap<>())
                        .put(uuid(rows.getBytes(2)), rows.getLong(3));
            }
        }
        return new TrustList(grants);
    }

    public void grant(Connection connection, UUID owner, UUID trusted, long since) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(grant)) {
            statement.setBytes(1, bytes(owner));
            statement.setBytes(2, bytes(trusted));
            statement.setLong(3, since);
            statement.executeUpdate();
        }
    }

    public void revoke(Connection connection, UUID owner, UUID trusted) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(REVOKE)) {
            statement.setBytes(1, bytes(owner));
            statement.setBytes(2, bytes(trusted));
            statement.executeUpdate();
        }
    }

    // Most significant half first: the one form a UUID has in 16 bytes.
    static byte[] bytes(UUID uuid) {
        return ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits()).array();
    }

    static UUID uuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
