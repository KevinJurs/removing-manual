package com.example.repository.exception;

public class NotNullConstraintException extends RepositoryException{
    public NotNullConstraintException(String message, Throwable cause){
        super(message, cause);
    }
}
