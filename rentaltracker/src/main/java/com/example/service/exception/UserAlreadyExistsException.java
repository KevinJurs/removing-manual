package com.example.service.exception;

public class UserAlreadyExistsException extends ServiceException{
    public UserAlreadyExistsException(String message, Throwable cause){
        super(message, cause);
    }
}
