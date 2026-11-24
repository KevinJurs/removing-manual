package com.example.service;

import com.example.service.exception.EmptyPasswordException;
import com.example.service.exception.EmptyUsernameException;
import com.example.service.exception.ServiceException;

public final class UserValidator {
    private UserValidator(){}

    public static String validateUsername(String username) throws ServiceException{
        if (username == null || username.isBlank()){
            throw new EmptyUsernameException("Username cannot be empty.");
        }           
        return username.strip();
    }

    public static void validatePassword(String password) throws ServiceException{
        if(password == null || password.isBlank()){
            throw new EmptyPasswordException("Password cannot be empty");
        }
    }
}
