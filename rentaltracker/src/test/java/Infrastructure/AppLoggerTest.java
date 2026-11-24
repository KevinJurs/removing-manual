package Infrastructure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.infrastructure.AppLogger;
import com.example.infrastructure.Database;
import com.example.repository.ItemRepository;
import com.example.repository.RentalRepository;
import com.example.repository.SchemaInitialiser;
import com.example.repository.UserRepository;
import com.example.repository.exception.NotNullConstraintException;
import com.example.repository.exception.RepositoryException;

public class AppLoggerTest {
    private Connection connection;
    private ItemRepository itemRepository;
    private UserRepository userRepository;
    private RentalRepository rentalRepository;
    private Path logFile;
    private AppLogger logger;

    @TempDir 
    Path temporaryDirectory;

    @BeforeEach
    void setUp() throws Exception{
        logFile = temporaryDirectory.resolve("app.log");
        logger = new AppLogger(logFile);
        Database database = new Database(temporaryDirectory.resolve("test.db"), logger);
        SchemaInitialiser initialiser = new SchemaInitialiser();
        connection = database.openConnection();

        try {
            initialiser.initialise(connection);
            itemRepository = new ItemRepository(connection, logger);
            userRepository = new UserRepository(connection, logger);
            rentalRepository = new RentalRepository(connection, logger);
        } catch (Exception e){
            try {
                connection.close();
            } catch (SQLException closeException){
                e.addSuppressed(closeException);
            }
            throw e;
        }
    }

    @AfterEach 
    void closeConnection() throws SQLException{
        if (connection != null && !connection.isClosed()){
            connection.close();
        }
    }

    @Test 
    //test for message and valid timestamp
    void logPrintsStructuredMessageWithValidTimestamp() throws IOException {
        logger.log("INFO", "user.login_success", "user_id=3");

        String result = Files.readString(logFile).trim();
        //message check
        assertTrue(result.contains(" | INFO | user.login_success | user_id=3"));

        String timestamp = result.split(" \\| ")[0];
        //timestamp check
        assertDoesNotThrow(() -> Instant.parse(timestamp));

        Instant parsedTimestamp = Instant.parse(timestamp);
        //timestamp check for fractional seconds
        assertEquals(0, parsedTimestamp.getNano());
    }

    @Test
    //test for checking that multiple messages are appended to the log file
    void logAppendsMessagesToFile() throws IOException {
        logger.log("INFO", "user.login_success", "user_id=3");
        logger.log("ERROR", "user.login_failed", "user_not_exist");

        String result = Files.readString(logFile).trim();

        //checks that two log messages are appended to the file
        assertTrue(result.contains(" | INFO | user.login_success | user_id=3"));
        assertTrue(result.contains(" | ERROR | user.login_failed | user_not_exist"));
    }

    @Test
    //test for checking that multiple messages are appended to the log file
    void appLoggerCatchesIOException() throws IOException {
        Path directory = Files.createDirectory(temporaryDirectory.resolve("logs"));
        logger = new AppLogger(directory);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        System.setErr(new PrintStream(outputStream));

        logger.log("INFO", "user.login_success", "user_id=3");

        assertTrue(outputStream.toString().contains("Failed to write log"));
    }

    @Test
    //test for checking that multiple messages are appended to the log file
    void logDatabaseErrorLogsConstraintException() throws Exception {
        assertThrows(NotNullConstraintException.class, () ->
            itemRepository.createItem(1, null, "Electric drill", 10));
        String result = Files.readString(logFile).trim();

        //checks that two log messages are appended to the file
        assertTrue(result.contains(" | ERROR | db.insert | op=createItem.itemRepo error=constraint_violation"));
    }

    @Test
    //test for checking that multiple messages are appended to the log file
    void logDatabaseErrorLogsForeignKeyViolation() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
        assertThrows(RepositoryException.class, () ->
            itemRepository.createItem(1, "Drill", "Electric drill", 10));
        String result = Files.readString(logFile).trim();

        //checks that two log messages are appended to the file
        assertTrue(result.contains(" | ERROR | db.insert | op=createItem.itemRepo error=foreign_key_violation"));
    }

    @Test
    //test for checking that multiple messages are appended to the log file
    void logDatabaseErrorLogsConnectionError() throws Exception {
        connection.close();
        assertThrows(RepositoryException.class, () ->
            itemRepository.createItem(1, "Drill", "Electric drill", 10));
        String result = Files.readString(logFile).trim();

        //checks that two log messages are appended to the file
        assertTrue(result.contains(" | ERROR | db.insert | op=createItem.itemRepo error=database_connection"));
    }

    @Test
    void logDatabaseErrorWithNullGetMessage() throws Exception {
        // getMessage() is null
        Exception exception = new SQLException();

        logger.logDatabaseError(exception, "test", "null");
        String result = Files.readString(logFile).trim();
        
        assertTrue(result.contains(" | ERROR | db.test | op=null error=database_connection"));
    }
} 

    
