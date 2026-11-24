package com.example.service.exception;

public class EmptyUsernameException extends ServiceException{
    public EmptyUsernameException(String message){
        super(message, null);
    }

}
