package repository;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.infrastructure.AppLogger;
import com.example.infrastructure.Database;
import com.example.infrastructure.exception.DatabaseInitialisationException;
import com.example.repository.SchemaInitialiser;

public class SchemaInitialiserTest {
    @TempDir 
    Path temporaryDirectory;

    @Test
    void initialiseWithClosedConnectionThrowsException() throws Exception {
        Path logFile = temporaryDirectory.resolve("app.log");
        AppLogger logger = new AppLogger(logFile);
        Database database = new Database(temporaryDirectory.resolve("test.db"), logger);
        SchemaInitialiser initialiser = new SchemaInitialiser();
        Connection connection = database.openConnection();
        connection.close();
        DatabaseInitialisationException exception = assertThrowsExactly(DatabaseInitialisationException.class, () -> initialiser.initialise(connection));
        assertInstanceOf(SQLException.class, exception.getCause());
    }

}
