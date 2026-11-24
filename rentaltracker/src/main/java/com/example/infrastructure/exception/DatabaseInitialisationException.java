package com.example.infrastructure.exception;

public class DatabaseInitialisationException extends Exception {
    public DatabaseInitialisationException(String message, Throwable cause){
        super(message, cause);
    }
}
