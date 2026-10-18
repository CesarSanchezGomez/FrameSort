package com.cesarcosmico.framesort.config;

import java.util.Locale;

/** MySQL and MariaDB share the SQL dialect and the migrations; each uses its own JDBC driver. */
public enum DatabaseType {
    SQLITE,
    MYSQL,
    MARIADB;

    // A typo must not silently switch to SQLite: the admin would believe the data goes to MySQL.
    public static DatabaseType parse(String raw) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "sqlite" -> SQLITE;
            case "mysql" -> MYSQL;
            case "mariadb" -> MARIADB;
            default -> throw new IllegalArgumentException(
                    "database.yml > type: unknown '" + raw + "' (expected sqlite, mysql or mariadb)");
        };
    }

    /** The folder under {@code db/} that holds this backend's migrations. */
    public String dialect() {
        return this == SQLITE ? "sqlite" : "mysql";
    }
}
