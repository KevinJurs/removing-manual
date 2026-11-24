package com.example.service;

import java.util.Optional;

import com.example.domain.User;
import com.example.infrastructure.PasswordHasher;
import com.example.repository.UserRepository;
import com.example.repository.exception.RepositoryException;
import com.example.repository.exception.UniqueConstraintException;
import com.example.service.exception.ServiceException;
import com.example.service.exception.UserAlreadyExistsException;
import com.example.infrastructure.AppLogger;

public class UserService {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher = new PasswordHasher();
    private final AppLogger logger;

    public UserService(UserRepository userRepository, AppLogger logger) {
        this.userRepository = userRepository;
        this.logger = logger;
    }

    public Optional<User> findUser(long id) throws ServiceException{
        try{
            return userRepository.findById(id);
        } catch (RepositoryException e) {
            throw new ServiceException("Could not load user.", e);
        }
    }

    public User register(String username, String password) throws ServiceException{
        try {
            username = UserValidator.validateUsername(username);
            UserValidator.validatePassword(password);
        } catch (ServiceException e) {
            logger.log("WARN", "validation.failed", "entity=User operation=register reason=invalid_input");

            throw e;
        }
        String passwordHash = passwordHasher.hash(password);
        try {
            User user = userRepository.createUser(username, passwordHash);
            logger.log("INFO", "user.registered", "user_id=" + user.getId() + " username= " + user.getUsername());
            return user;
        } catch (UniqueConstraintException e){
            logger.log("WARN", "registration.failed", "reason=username_already_exists");
            throw new UserAlreadyExistsException("The username is already taken", e);
        } catch (RepositoryException e){
            throw new ServiceException("Could not register user", e);
        }
    }

    public Optional<User> login(String username, String password) throws ServiceException{
        try {
        username = UserValidator.validateUsername(username);
        UserValidator.validatePassword(password);

        } catch (ServiceException e) {
            logger.log("WARN", "validation.failed", "entity=User operation=login reason=invalid_input");
            
            throw e;
        }
        Optional<UserRepository.Credentials> result;
        try {
            result = userRepository.findCredentialsByUsername(username);
        }catch (RepositoryException e){
            throw new ServiceException("Could not log in", e);
        }
        if (result.isEmpty()){
            logger.log("WARN", "user.login.failed", "reason=invalid_credentials");
            return Optional.empty();
        }
        UserRepository.Credentials credentials = result.get();
        String storedHash = credentials.passwordHash();
        try {
            return passwordHasher.matches(password, storedHash) ? Optional.of(credentials.user()) : Optional.empty();
        } catch (RuntimeException e){
            throw new ServiceException("Could not log in", null);
        }
    }
}
