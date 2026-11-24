package com.example.service.exception;

public class EmptyItemnameException extends ServiceException {
    public EmptyItemnameException(String message){
        super(message, null);
    }
}
