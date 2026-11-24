package com.example.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;

import org.sqlite.SQLiteException;

import com.example.domain.User;
import com.example.infrastructure.AppLogger;
import com.example.repository.exception.MappingException;
import com.example.repository.exception.NotFoundException;
import com.example.repository.exception.NotNullConstraintException;
import com.example.repository.exception.RepositoryException;
import com.example.repository.exception.UniqueConstraintException;

public class UserRepository {
    private final Connection connection;
    private final AppLogger logger;
    private static final DateTimeFormatter DATABASE_TIME_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);


    public UserRepository(Connection connection, AppLogger logger){
        this.connection = connection;
        this.logger = logger;
    }

    // Database to domain conversion helper
    private User mapUser(ResultSet results) throws MappingException{
        try {
            String username = results.getString("username");
            long id = results.getLong("id");
            LocalDateTime createdAt = LocalDateTime.parse(results.getString("created_at"), DATABASE_TIME_FORMAT);
            return new User(id, username, createdAt);
        } catch (SQLException | DateTimeParseException e){
            logger.log("ERROR", "mapUser.failed", "mapping_exception");
            throw new MappingException("Could not map database record to user.", e);
        }
    }

    public User createUser(String username, String passwordHash) throws RepositoryException{
        String sql = "INSERT INTO users (username, password) VALUES (?, ?) RETURNING id, username, created_at"; // creates the user and returns the inserted row
        try (PreparedStatement statement = connection.prepareStatement(sql)){
            statement.setString(1, username);
            statement.setString(2, passwordHash);
            try (ResultSet results = statement.executeQuery()){
                results.next();
                return mapUser(results);
            }
        } catch (SQLException e) {
            logger.logDatabaseError(e, "insert", "createUser.userRepo");
            throw translateFailure("Could not create user.", e);
        }
    }
    

    public Optional<User> findByUsername(String username) throws RepositoryException {
    String sql = "SELECT id, username, created_at FROM users WHERE username = ?";
    Optional<User> user;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setString(1, username);
        try (ResultSet results = statement.executeQuery()) {
            if (results.next()) {
                user = Optional.of(mapUser(results));
            } else {
                user = Optional.empty();
            }
        }
    } catch (SQLException e) {
        logger.logDatabaseError(e, "query", "findByUsername.userRepo");
        throw translateFailure("Could not find user by username", e);
    }
    return user;
}
    public Optional<User> findById(long id) throws RepositoryException{
        String sql = "SELECT id, username, created_at FROM users WHERE id = ?";
        Optional<User> user;
        try (PreparedStatement statement = connection.prepareStatement(sql)){
            statement.setLong(1, id);
            try (ResultSet results = statement.executeQuery()){
                if(results.next()){
                    user = Optional.of(mapUser(results));
                } else {
                    user =  Optional.empty();
                }
            }
        } catch (SQLException e){
            logger.logDatabaseError(e, "query", "findById.userRepo");
            throw translateFailure("Could not find user by id", e);
        }
    return user;
    }

    public User getById(long id) throws RepositoryException{
        return findById(id).orElseThrow(() -> new NotFoundException("User was not found"));
    }
    
    // For consistent error handling, distinguishing duplicate values and missing fields
    private RepositoryException translateFailure (String message, SQLException cause){
        if (cause instanceof SQLiteException sqliteException){
            switch (sqliteException.getResultCode()) {
                case SQLITE_CONSTRAINT_UNIQUE:
                    return new UniqueConstraintException("Username already exists", cause);
                case SQLITE_CONSTRAINT_NOTNULL:
                    return new NotNullConstraintException("A required user field is missing", cause);
                default:
                    break;
            }
        }
        return new RepositoryException(message, cause);
    }

    public static final class Credentials{
        private final User user;
        private final String passwordHash;
        private Credentials(User user, String passwordHash){
            this.user = user;
            this.passwordHash = passwordHash;
        }

        public User user(){
            return user;
        }

        public String passwordHash(){
            return passwordHash;
        }
    }

    public Optional<Credentials> findCredentialsByUsername(String username) throws RepositoryException{
        String sql = "SELECT id, username, created_at, password FROM users WHERE username = ?";
        Optional<Credentials> credentials;
        try (PreparedStatement statement = connection.prepareStatement(sql)){
            statement.setString(1, username);
            try (ResultSet results = statement.executeQuery()){
                if(!results.next()){
                    credentials = Optional.empty();
                } else {
                    credentials = Optional.of(new Credentials(mapUser(results), results.getString("password")));
                }
            }
        } catch (SQLException e){
            logger.logDatabaseError(e, "query", "findCredentialsByUsername.userRepo");
            throw translateFailure("Could not load credentials", e);
        }
        return credentials;
    }
}
