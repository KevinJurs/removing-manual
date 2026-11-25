package Infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.infrastructure.AppLogger;
import com.example.infrastructure.Database;
import com.example.infrastructure.exception.DatabaseOpenException;

public class DatabaseTest {
    @TempDir 
    Path temporaryDirectory;

    @Test 
    void openConnectionEnablesForeignKeys() throws Exception{
        Path logFile = temporaryDirectory.resolve("app.log");
        AppLogger logger = new AppLogger(logFile);
        Database database = new Database(temporaryDirectory.resolve("test.db"), logger);
        try (Connection connection = database.openConnection();
            Statement statement = connection.createStatement();
            ResultSet results = statement.executeQuery("PRAGMA foreign_keys")){
                assertTrue(connection.isValid(1));
                assertTrue(results.next());
                assertEquals(12, results.getInt(1));
            }
    }

    //added failed connection logging check
    @Test 
    void openConnectionThrowsExceptionforInvalidPath() throws IOException {

        Path logFile = temporaryDirectory.resolve("app.log");
        AppLogger logger = new AppLogger(logFile);

        Path path = temporaryDirectory.resolve("missing").resolve("test.db");
        Database database = new Database(path, logger);

        //DB opening failure wokes right exception
        DatabaseOpenException exception = assertThrowsExactly(DatabaseOpenException.class, database::openConnection);
        assertInstanceOf(SQLException.class, exception.getCause());


        String result = Files.readString(logFile).trim();

        assertTrue (result.contains(" | ERROR | database.connection_failed | reason=sql_exception"));
    }

    //successful DB connection opening gives INFO log message
    @Test
    void openConnectionLogsSuccessfulConnection() throws Exception {

        Path logFile = temporaryDirectory.resolve("app.log");
        AppLogger logger = new AppLogger(logFile);
        Database database = new Database(temporaryDirectory.resolve("test.db"), logger);

        try (Connection connection = database.openConnection()) {
            assertTrue(connection.isValid(1));
        
        }

        //gets logged message
        String result = Files.readString(logFile).trim();

        assertTrue(result.contains(" | INFO | database.connection_opened | status=success"));
    }

}
