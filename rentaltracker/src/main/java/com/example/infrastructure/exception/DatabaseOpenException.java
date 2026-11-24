package com.example.infrastructure.exception;

public class DatabaseOpenException extends Exception{
    public DatabaseOpenException(String message, Throwable cause){
        super(message, cause);
    }
}
