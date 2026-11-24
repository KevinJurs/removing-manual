package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;

import com.example.infrastructure.AppLogger;
import com.example.infrastructure.Database;
import com.example.infrastructure.exception.DatabaseOpenException;
import com.example.repository.SchemaInitialiser;
import com.example.repository.UserRepository;
import com.example.service.StartupService;
import com.example.service.UserService;
import com.example.transport.Transport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class MainTest {


    private final PrintStream originalOut = System.out;
    private final InputStream originalIn = System.in;
    private final PrintStream originalErr = System.err;

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setIn(originalIn);
        System.setErr(originalErr);
        }

    @TempDir
    Path temporaryDirectory;


    @Test
    void mainStartsAppAndExitsSuccessfully() throws Exception {

        //temp log file to keep test isolated
        Path logFile = temporaryDirectory.resolve("app.log");
        AppLogger logger = new AppLogger(logFile);

        String input = """
            2
            Juhan
            password
            1
            Juhan
            password
            5
            """;

        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes());
        System.setIn(inputStream);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        Main.run(temporaryDirectory.resolve("test.db"), logger);

        assertTrue(outputStream.toString().contains("Database is ready."));
        assertTrue(outputStream.toString().contains("Goodbye!"));

        //verifies that opening and closing connection are logged
        String logContent = Files.readString(logFile);
        assertTrue(logContent.contains("INFO | database.connection_opened | status=success"));
        assertTrue(logContent.contains("INFO | database.connection_closed | status=success"));
    }

    @Test
    void mainCreatesUserInDatabaseAndLoadsSameUserOnRelaunch() throws Exception {
        Path databasePath = temporaryDirectory.resolve("test.db");
        Path logFile = temporaryDirectory.resolve("app.log");
        AppLogger logger = new AppLogger(logFile);
        // first launch. Register, log in and exit
        String input = """
            2
            Juhan
            password
            1
            Juhan
            password
            5
            """;

        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes());
        System.setIn(inputStream);
        Main.run(databasePath, logger);
        Database database = new Database(databasePath, logger);
        long originalUserId;
        try (Connection connection = database.openConnection()){
            UserRepository userRepository = new UserRepository(connection, logger);
            originalUserId = userRepository.findByUsername("Juhan").orElseThrow().getId();
        }

        // second launch. Log in with the saved account and exit
        System.setIn(new ByteArrayInputStream("""
            1
            Juhan
            password
            5
            """.getBytes()));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
        Main.run(databasePath, logger);
        String output = outputStream.toString();
        int loginPrompt = output.indexOf("Do you have an account?");
        int mainMenu = output.indexOf("1) List an item");
        assertTrue(mainMenu > loginPrompt, output);
        assertTrue(output.contains("Goodbye!"), output);

        try (Connection connection = database.openConnection(); Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT id, username FROM users")){
            assertTrue(rows.next());
            assertEquals(originalUserId, rows.getLong("id"));
            assertEquals("Juhan", rows.getString("username"));
            assertFalse(rows.next());
        }

    }
    
    @Test 
    void mainHandlesDatabaseOpenException() {
        Path logFile = temporaryDirectory.resolve("app.log");
        AppLogger logger = new AppLogger(logFile);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        Path invalidPath = temporaryDirectory.resolve("no-folder/test.db");

        Main.run(invalidPath, logger);

        assertTrue(outputStream.toString().contains("Could not open the database."));
    }

    @Test 
    void mainHandlesDatabaseInitialisationException() throws Exception {
        Path logFile = temporaryDirectory.resolve("app.log");
        AppLogger logger = new AppLogger(logFile);
        Path databasePath = temporaryDirectory.resolve("test.db");
        Database database = new Database(databasePath, logger);

        //opens DB connection and creates statement to run SQL commands
        try (Connection connection = database.openConnection();
            Statement statement = connection.createStatement()) {

            // creates table with one column: id
            statement.executeUpdate("CREATE TABLE test_table (id INTEGER)");

            // creates index named users to cause conflict with users table
            statement.executeUpdate("CREATE INDEX users ON test_table(id)");

            }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        Main.run(databasePath, logger);

        assertTrue(outputStream.toString().contains("Could not initialise the database."));

        }

    // @Test 
    // void mainHandlesTransportException() throws Exception {

        
    //     Path databasePath = temporaryDirectory.resolve("test.db");
    //     Database database = new Database(databasePath);

    //     //creates users table without expected columns
    //     try (Connection connection = database.openConnection(); Statement statement = connection.createStatement()) {

    //         statement.executeUpdate("CREATE TABLE users (id INTEGER PRIMARY KEY)");
    //     }
    //     System.setIn(new ByteArrayInputStream("""
    //         1
    //         Juhan
    //         password
    //         """.getBytes()));

    //     ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
    //     System.setErr(new PrintStream(errorStream));

    //     Main.run(databasePath);

    //     //assertTrue(errorStream.toString().contains("Could not load user."));
    //     assertTrue(errorStream.toString().contains("Could not log in"));
    //     }

    @Test
    void mainHandlesTransportexception() throws Exception{
        Path logFile = temporaryDirectory.resolve("app.log");
        AppLogger logger = new AppLogger(logFile);
        Path databasepath = temporaryDirectory.resolve("test.db");
        Database database = new Database(databasepath, logger);
        try(Connection connection = database.openConnection()){
            new SchemaInitialiser().initialise(connection);
            UserService userService = new UserService(new UserRepository(connection, logger), logger);
            userService.register("Juhan", "password");
            try(Statement statement = connection.createStatement()){
                statement.executeUpdate("DROP TABLE listed_items");
                statement.executeUpdate("CREATE TABLE listed_items (item_id INTEGER PRIMARY KEY)");
            }
        }
        System.setIn(new ByteArrayInputStream("""
            1
            Juhan
            password
            2
            5
            """.getBytes()));
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errorStream));
        Main.run(databasepath, logger);
        
        assertTrue(errorStream.toString().contains("Could not get the item list"));
    }

    @Test
    void mainConstructorWorks() {   

        // creates instance using main's default constructor
        Main main = new Main();

        assertNotNull(main);
        
        }

    @Test
    void mainHandlesDatabaseConectionCloseFailure() throws Exception {

        Path logFile = temporaryDirectory.resolve("app.log");
        AppLogger logger = new AppLogger(logFile);

        // creates mock database connection
        Connection connection = mock(Connection.class);

        // simulates SQLException when closing connection
        doThrow(new SQLException("Close failed"))
                .when(connection).close();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        try (MockedConstruction<Database> mockedDatabase =
            mockConstruction(Database.class, (mock, context) -> {

                // returns mock connection when database is opened
                when(mock.openConnection()).thenReturn(connection);

        });

        MockedConstruction<StartupService> mockedStartup = mockConstruction(StartupService.class);

        MockedConstruction<Transport> mockedTransport = mockConstruction(Transport.class)) {

            Main.run(temporaryDirectory.resolve("test.db"), logger);

            //verifys that closing connectipn was attempted
            verify(connection).close();

            assertTrue(outputStream.toString()
                    .contains("Could not close the database connection."));

            //verifies that connection closing failure is logged
            String logContent = Files.readString(logFile);
            assertTrue(logContent.contains("ERROR | database.connection_close_failed | reason=sql_exception"));   
            
            //verifies that failed closure is not logged as successful
            assertFalse(logContent.contains("INFO | database.connection_closed | status=success"));
        }
    }

    @Test
    void mainUsesDefaultDatabasePath() throws Exception {
        
        List<Object> constructorArguments = new ArrayList<>();
        Connection connection = mock(Connection.class);

        // mocks dependencies to avoid using real DB and CLI
        try (MockedConstruction<Database> mockedDatabase = 
                mockConstruction(Database.class, (mock, context) -> {

                constructorArguments.addAll(context.arguments());
                    when(mock.openConnection()).thenReturn(connection);
                });
            MockedConstruction<StartupService> mockedStartup =
                mockConstruction(StartupService.class);
            MockedConstruction<AppLogger> mockedLogger =
                mockConstruction(AppLogger.class);
            MockedConstruction<Transport> mockedTransport =
                mockConstruction(Transport.class)) {

            // starts the app without arguments
            Main.main(new String[0]);

            // verifys that default database path was used
            assertEquals(1, mockedDatabase.constructed().size());
            assertEquals(Path.of("rental-tracker.db"), constructorArguments.get(0));
        }
    }

}
