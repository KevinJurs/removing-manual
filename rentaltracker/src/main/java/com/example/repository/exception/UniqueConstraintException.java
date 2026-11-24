package com.example.repository.exception;

public class UniqueConstraintException extends RepositoryException{
    public UniqueConstraintException(String message, Throwable cause){
        super(message, cause);
    }
}
