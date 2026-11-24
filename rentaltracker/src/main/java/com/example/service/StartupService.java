package com.example.service;

import java.sql.Connection;

import com.example.infrastructure.exception.DatabaseInitialisationException;
import com.example.repository.SchemaInitialiser;

public class StartupService {
    private final SchemaInitialiser schemaInitialiser;

    public StartupService(SchemaInitialiser schemaInitialiser){
        this.schemaInitialiser = schemaInitialiser;
    }

    public void initialiseDatabase(Connection connection) throws DatabaseInitialisationException{
        schemaInitialiser.initialise(connection);
    }

}
