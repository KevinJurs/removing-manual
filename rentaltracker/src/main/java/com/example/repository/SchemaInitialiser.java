package com.example.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import com.example.infrastructure.exception.DatabaseInitialisationException;

public class SchemaInitialiser {

    // initialises required database tables
    public void initialise(Connection connection) throws DatabaseInitialisationException{
        String createUsers = """
            CREATE TABLE IF NOT EXISTS users(
                id INTEGER PRIMARY KEY,
                username TEXT NOT NULL UNIQUE,
                password TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
            """;
        String createItems = """
            CREATE TABLE IF NOT EXISTS listed_items(
                item_id INTEGER PRIMARY KEY,
                owner_id INTEGER NOT NULL,
                item_name TEXT NOT NULL,
                description TEXT NOT NULL,
                cost_per_day INTEGER NOT NULL CHECK (cost_per_day >= 0),
                status TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (owner_id) REFERENCES users(id),
                CHECK (status IN('available', 'rented', 'unlisted'))
                )
            """;
        String createRentals = """
            CREATE TABLE IF NOT EXISTS rentals (
                rental_id INTEGER PRIMARY KEY,
                item_id INTEGER NOT NULL,
                renter_id INTEGER NOT NULL,
                start_time TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                end_time TEXT NOT NULL,
                returned_at TEXT,
                status TEXT NOT NULL CHECK (status IN('active', 'closed')),
                FOREIGN KEY (item_id) REFERENCES listed_items(item_id),
                FOREIGN KEY (renter_id) REFERENCES users(id)
                )
            """;

        try (Statement statement = connection.createStatement()){
            statement.executeUpdate(createUsers);
            statement.executeUpdate(createItems);
            statement.executeUpdate(createRentals);

        } catch (SQLException e) {
            throw new DatabaseInitialisationException("Could not initialise the database.", e);
        }
    }

}
