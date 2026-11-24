package repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteException;

import com.example.domain.User;
import com.example.infrastructure.AppLogger;
import com.example.infrastructure.Database;
import com.example.repository.SchemaInitialiser;
import com.example.repository.UserRepository;
import com.example.repository.exception.MappingException;
import com.example.repository.exception.NotFoundException;
import com.example.repository.exception.NotNullConstraintException;
import com.example.repository.exception.RepositoryException;
import com.example.repository.exception.UniqueConstraintException;

public class UserRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    private Connection connection;
    private UserRepository repository;
    private Path logFile;
    private AppLogger logger;

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
    void createUserStoresUserInDatabase() throws Exception{
        repository.createUser("name", "password");
        String sql = "SELECT id, username, created_at, password FROM users WHERE username = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)){
            statement.setString(1, "name");
            try(ResultSet results = statement.executeQuery()){
                assertTrue(results.next(), "User should be stored");
                
                assertTrue(results.getLong("id") > 0);
                assertEquals("name", results.getString("username"));
                assertNotNull(results.getString("password"));

                String createdAt = results.getString("created_at");
                assertNotNull(createdAt);
                assertFalse(createdAt.isBlank());
            }
        }
    }

    @Test
    void duplicateUsernameThrowsUniqueConstraintException() throws Exception{
        repository.createUser("name", "password");
        assertThrowsExactly(UniqueConstraintException.class, () -> repository.createUser( "name", "password"));
        assertEquals(1L, countUsers());

    }

    @Test
    void nullUsernameThrowsNotNullConstraintException() throws Exception{
        assertThrowsExactly(NotNullConstraintException.class, () -> repository.createUser(null, "password"));
        assertEquals(0L, countUsers());
    }

    @Test 
    void nullPasswordThrowsNotNullConstraintException() throws Exception{
        assertThrowsExactly(NotNullConstraintException.class, () -> repository.createUser("name", null));
        assertEquals(0, countUsers());
    }

    private long countUsers() throws SQLException{
        String sql = "SELECT COUNT(*) FROM users";
        try (Statement statement = connection.createStatement(); ResultSet results = statement.executeQuery(sql)){
            if (!results.next()){
                throw new SQLException("Count query returned no row");
            }
            return results.getLong(1);
        }
    }

    @Test
    void findByUsernameForExistingUsername() throws Exception{
        User createdUser = repository.createUser("name", "password");
        Optional<User> result = repository.findByUsername("name");
        assertTrue(result.isPresent());
        User foundUser = result.get();
        assertEquals(createdUser.getId(), foundUser.getId());
        assertEquals("name", foundUser.getUsername());
    }

    @Test
    void findByUsernameForMissingUsername() throws Exception{
        repository.createUser("name", "password");
        Optional<User> result = repository.findByUsername("name2");
        assertTrue(result.isEmpty());
    }

    @Test 
    void findByIdForExistingId() throws Exception{
        User createdUser = repository.createUser("name", "password");
        Optional<User> result = repository.findById(createdUser.getId());
        assertTrue(result.isPresent());
        User foundUser = result.get();
        assertEquals(createdUser.getId(), foundUser.getId());
        assertEquals("name", foundUser.getUsername());
    }

    @Test
    void findByIdForMissingId() throws Exception{
        repository.createUser("name", "password");
        Optional<User> result = repository.findById(2);
        assertTrue(result.isEmpty());
    }

    @Test
    void getByIdReturnsExistingUser() throws Exception{
        User createdUser = repository.createUser("name", "password");
        User returnedUser = repository.getById(createdUser.getId());
        assertEquals(createdUser.getId(), returnedUser.getId());
        assertEquals(createdUser.getUsername(), returnedUser.getUsername());
        assertEquals(createdUser.getCreatedAt(), returnedUser.getCreatedAt());
    }

    @Test
    void getByIdThrowsRepositoryExceptionforMissingUser() throws Exception{       
        assertThrowsExactly(NotFoundException.class, () -> repository.getById(1));
    }

    @Test
    void invalidTimestampThrowsMappingException() throws Exception{
        User createdUser = repository.createUser("name", "password");
        String sql = "UPDATE users SET created_at = ? WHERE id = ?";
        try(PreparedStatement statement = connection.prepareStatement(sql)){
            statement.setString(1, "invalid timestamp");
            statement.setLong(2, createdUser.getId());
            assertEquals(1, statement.executeUpdate());
        }
        MappingException exception = assertThrowsExactly(MappingException.class, () -> repository.findById(createdUser.getId()));
        assertInstanceOf(DateTimeParseException.class, exception.getCause());
    }

    @Test
    void findByUsernameWithclosedConnectionThrowsRepositoryException() throws Exception{
        connection.close();
        RepositoryException exception = assertThrowsExactly(RepositoryException.class, () -> repository.findByUsername("name"));
        assertInstanceOf(SQLException.class, exception.getCause());
    }

    @Test 
    void findByIdWithClosedConnectionThrowsRepositoryexception() throws Exception{
        connection.close();
        RepositoryException exception = assertThrowsExactly(RepositoryException.class, () -> repository.findById(1));
        assertInstanceOf(SQLException.class, exception.getCause());
    }

    @Test 
    void missingUsersTableThrowsRepositoryException() throws Exception{
        try(Statement statement = connection.createStatement()){
            statement.executeUpdate("ALTER TABLE users RENAME TO unavailable_users");
        }
        RepositoryException exception = assertThrowsExactly(RepositoryException.class, () -> repository.findByUsername("name"));
        assertInstanceOf(SQLiteException.class, exception.getCause());
    }

}
