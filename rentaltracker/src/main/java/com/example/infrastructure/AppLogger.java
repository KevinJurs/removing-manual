package com.example.infrastructure;

import java.time.temporal.ChronoUnit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public class AppLogger {
    
    private final Path logFilePath;

    public AppLogger(Path logFilePath) {
        this.logFilePath = logFilePath;
    }

    //logs event with given parameters
    public void log(String level, String event, String context) {
        
        //instant gives also timezone mark Z
        String timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        String message = timestamp + " | " + level + " | " + event + " | " + context;
        try {
            Files.writeString(logFilePath, message + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to write log: " + e.getMessage());
        }
    }

    public void logDatabaseError(Exception e, String CRUD, String method) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        String error;
        if (message.contains("foreign key")) {
            error = "foreign_key_violation";
        } else if (message.contains("constraint")) {
            error = "constraint_violation";
        } else {
            error = "database_connection";
        }
        log("ERROR", "db." + CRUD, "op=" + method + " error=" + error);
    }
}
