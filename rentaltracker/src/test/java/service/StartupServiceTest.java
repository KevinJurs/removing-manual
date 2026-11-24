package service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.infrastructure.AppLogger;
import com.example.infrastructure.Database;
import com.example.repository.SchemaInitialiser;
import com.example.service.StartupService;

public class StartupServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test 
    void initialiseDatabaseCreatesTable() throws Exception{
        Path logFile = temporaryDirectory.resolve("app.log");
        AppLogger logger = new AppLogger(logFile);
        Database database = new Database(temporaryDirectory.resolve("test.db"), logger);
        SchemaInitialiser schemaInitialiser = new SchemaInitialiser();
        StartupService startupService = new StartupService(schemaInitialiser);

        try (Connection connection = database.openConnection()){
            startupService.initialiseDatabase(connection);
            try (Statement statement = connection.createStatement(); ResultSet results = statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")){
                Set<String> tables = new HashSet<>();
                while (results.next()){
                    tables.add(results.getString("name"));
                }
                assertTrue(tables.contains("users"));
                assertTrue(tables.contains("listed_items"));
                assertTrue(tables.contains("rentals"));
            }
        }
    }

}
