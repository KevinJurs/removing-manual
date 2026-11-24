package com.example.transport.exception;
import com.example.service.exception.ServiceException;

public class TransportException extends ServiceException {
    public TransportException(String message, Throwable cause){
        super(message, cause);
    }
}



