package com.cesarcosmico.framesort.config;

public record DatabaseSettings(DatabaseType type, String sqliteFile, Remote remote) {

    public record Remote(String host, int port, String database, String username, String password,
                         String properties) {
    }

    public static DatabaseSettings sqlite(String file) {
        return new DatabaseSettings(DatabaseType.SQLITE, file, new Remote("localhost", 3306, "", "", "", ""));
    }

    public static DatabaseSettings parse(ConfigReader config) {
        ConfigReader remote = config.sectionOrEmpty("remote");
        return new DatabaseSettings(
                DatabaseType.parse(config.string("type", "sqlite")),
                config.string("sqlite.file", "data.db"),
                new Remote(
                        remote.string("host", "localhost"),
                        remote.integer("port", 3306, 1, 65535),
                        remote.string("database", ""),
                        remote.string("username", ""),
                        remote.string("password", ""),
                        remote.string("properties", "")));
    }
}
