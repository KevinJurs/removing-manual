package com.example.repository.exception;

public class RepositoryException extends Exception {
    public RepositoryException(String message, Throwable cause){
        super(message, cause);
    }

    public RepositoryException(String message){
        super(message);
    }
}
