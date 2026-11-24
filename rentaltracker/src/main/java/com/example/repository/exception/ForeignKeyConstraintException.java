package com.example.repository.exception;

public class ForeignKeyConstraintException extends RepositoryException {
    public ForeignKeyConstraintException(String message, Throwable cause){
        super(message, cause);
    }
}
