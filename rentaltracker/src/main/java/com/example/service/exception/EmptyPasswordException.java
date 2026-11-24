package com.example.service.exception;

public class EmptyPasswordException extends ServiceException{
    public EmptyPasswordException(String message){
        super(message, null);
    }

}
