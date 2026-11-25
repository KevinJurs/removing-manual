package com.example.infrastructure;

import java.nio.file.Path;

public class PathConfig {
    private PathConfig(){}

     String dbPathStr = System.getenv("DB_PATH");
    Path dbPath = dbPathStr != null ? Path.of(dbPathStr) : Path.of("rental-tracker.db");

    public static Path resolvePath(String env, String defaultPath) {
        String path = System.getenv(env);
        return path != null ? Path.of(path) : Path.of(defaultPath);
    }

    public static Path getDBPath() {
        return resolvePath("DB_PATH", "rental-tracker.db");
    }

    public static Path getLogPath() {
        return resolvePath("LOG_PATH", "rental-tracker.log");
    }
}
