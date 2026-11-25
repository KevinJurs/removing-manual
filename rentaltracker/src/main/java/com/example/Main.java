package com.example;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Scanner;

import com.example.infrastructure.AppLogger;
import com.example.infrastructure.Database;
import com.example.infrastructure.PathConfig;
import com.example.infrastructure.exception.DatabaseInitialisationException;
import com.example.infrastructure.exception.DatabaseOpenException;
import com.example.repository.ItemRepository;
import com.example.repository.RentalRepository;
import com.example.repository.SchemaInitialiser;
import com.example.repository.UserRepository;
import com.example.service.ItemService;
import com.example.service.RentalService;
import com.example.service.StartupService;
import com.example.service.UserService;
import com.example.transport.Transport;
import com.example.transport.exception.TransportException;

public class Main {
    
    //starts application using default database
    public static void main(String[] args) {
        run(PathConfig.getDBPath(), new AppLogger(PathConfig.getLogPath()));
    }

    //runs application with provided logger
    static void run(Path databasePath, AppLogger logger) {

        Database database = new Database(databasePath, logger);
        SchemaInitialiser initialiser = new SchemaInitialiser();
        StartupService startupService = new StartupService(initialiser);
        
    try{
        try (Connection connection = database.openConnection()){
            startupService.initialiseDatabase(connection);
            System.out.println("Database is ready.");
            //test
            Scanner sc = new Scanner(System.in);

            ItemRepository itemRepository = new ItemRepository(connection, logger);
            UserRepository userRepository = new UserRepository(connection, logger);
            RentalRepository rentalRepository = new RentalRepository(connection, logger);

            ItemService itemService = new ItemService(itemRepository, rentalRepository, logger);
            UserService userService = new UserService(userRepository, logger);
            RentalService rentalService = new RentalService(rentalRepository, itemService, userService, logger);

            Transport transport = new Transport(itemService, userService, rentalService, logger);
            transport.CLI(connection, sc);

        }

        logger.log("INFO", "database.connection_closed", "status=success");
        
        } catch (DatabaseOpenException e){
            System.out.println(e.getMessage());
        } catch (DatabaseInitialisationException e){
            logger.log("ERROR", "database.initialisation_failed", "reason=schema_setup_failed");
            System.out.println(e.getMessage());
        } catch (TransportException e){
            System.err.println(e.getMessage());
        } catch (SQLException e){
            logger.log("ERROR", "database.connection_close_failed", "reason=sql_exception");
            System.out.println("Could not close the database connection.");
        }
    }
}