package service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.example.domain.User;
import com.example.infrastructure.AppLogger;
import com.example.infrastructure.Database;
import com.example.infrastructure.PasswordHasher;
import com.example.repository.SchemaInitialiser;
import com.example.repository.UserRepository;
import com.example.service.UserService;
import com.example.service.exception.EmptyUsernameException;
import com.example.service.exception.ServiceException;
import com.example.service.exception.UserAlreadyExistsException;

public class UserServiceTest {
    private Connection connection;
    private UserService service;
    private UserRepository repository;
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
            repository = new UserRepository(connection, logger);
            service = new UserService(repository, logger);
        } catch (Exception e){
            try {
                connection.close();
            } catch (SQLException closException){
                e.addSuppressed(closException);
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
    void findUserReturnsEmptyOptionalIfUserDoesNotExist() throws Exception{
        Optional<User> user = service.findUser(1);
        assertTrue(user.isEmpty());
    }

    @Test
    void findUserThrowsServiceExceptionWithClosedConnection() throws Exception{
        connection.close();
        assertThrowsExactly(ServiceException.class, () -> service.findUser(1));
    }

    private long countUsers() throws SQLException{
        try(Statement statement = connection.createStatement(); ResultSet results = statement.executeQuery("SELECT COUNT(*) FROM users")){        
            assertTrue(results.next());
            return results.getLong(1);
        }
    }

    @Test 
    void registerStoresAHashThatVerifiesThePassword() throws Exception{
        String password = " password ";
        User user = service.register("name", password);
        String hash = readStroredHash(user.getId());
        assertNotNull(hash);
        assertNotEquals(password, hash);
        assertTrue(new PasswordHasher().matches(password, hash));
    }

    @Test 
    void registerRejectsDuplicateusernameAndPreservesOriginalHash() throws Exception{
        User user = service.register("name", "password");
        String hash = readStroredHash(user.getId());
        assertThrowsExactly(UserAlreadyExistsException.class, () -> service.register("name", "password2"));
        assertEquals(1, countUsers());
        assertEquals(hash, readStroredHash(user.getId()));
    }

    @Test 
    void registerThrowsEmptyUsernameExceptionWithBlankUsername() throws Exception{
        assertThrowsExactly(EmptyUsernameException.class, () -> service.register("  ", "password"));
    }

    @Test 
    void registerWithClosedConnectionThrowsServiceException()throws Exception{
        connection.close();
        assertThrowsExactly(ServiceException.class, () -> service.register("name", "password"));
    }

    @Test 
    void loginReturnsRegisteredUserForCorrectpassword() throws Exception{
        User registered = service.register("name", "password");
        User loggedIn = service.login("name", "password").orElseThrow();
        assertEquals(registered.getId(), loggedIn.getId());
    }

    @Test 
    void loginReturnsEmptyForWrongPassword() throws Exception{
        service.register("name", "password");
        assertTrue(service.login("name", "wrong-password").isEmpty());
    }

    @Test 
    void loginReturnEmptyForUnknownUsername() throws Exception{
        service.register("name", "password");
        assertTrue(service.login("unknown-name", "password").isEmpty());
    }

    @Test 
    void loginWithMalformedStoredhashThrowsServiceException() throws Exception{
        repository.createUser("name", "invalid-hash");
        ServiceException exception = assertThrowsExactly(ServiceException.class, () -> service.login(("name"), "password"));
        assertEquals("Could not log in", exception.getMessage());
    }

    @Test 
    void loginWithClosedConnectionThrowsServiceException() throws Exception{
        service.register("name", "password");
        connection.close();
        assertThrowsExactly(ServiceException.class, () -> service.login("name", "password"));
    }

    private String readStroredHash(long userId) throws SQLException{
        String sql = "SELECT password FROM users WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)){
            statement.setLong(1, userId);
            try (ResultSet results = statement.executeQuery()){
                assertTrue(results.next(), "Expected a stored user");
                return results.getString("password");
            }
        }
    }

    @Test 
    void loginThrowsEmptyUsernameExceptionWithBlankUsername() throws Exception{
        assertThrowsExactly(EmptyUsernameException.class, () -> service.login("  ", "password"));
    }
}
