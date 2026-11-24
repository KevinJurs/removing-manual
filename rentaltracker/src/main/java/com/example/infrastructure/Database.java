package com.example.infrastructure;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.example.infrastructure.exception.DatabaseOpenException;

public class Database {

    private final Path databasePath;
    private final AppLogger logger;

    // public Database(Path databasePath){
    //     this(databasePath, new AppLogger());
    // }
    public Database(Path databasePath, AppLogger logger){
        this.databasePath = databasePath.toAbsolutePath();
        this.logger = logger;
    }

    public Connection openConnection() throws DatabaseOpenException{
        try{
            String url = "jdbc:sqlite:" + databasePath + "?foreign_keys=on";

            Connection connection = DriverManager.getConnection(url);

            //logs when DB connection is opened successfully
            logger.log("INFO", "database.connection_opened", "status=success");

            return connection;

        } catch (SQLException e){

            //logs when DB connection opening fails
            logger.log("ERROR", "database.connection_failed", "reason=sql_exception");

            throw new DatabaseOpenException("Could not open the database.", e);
        }
    }

    // public Connection openConnection() throws DatabaseOpenException{
    //     Connection connection;
    //     try{
    //         String url = "jdbc:sqlite:" + databasePath;
    //         connection = DriverManager.getConnection(url);
    //     } catch (SQLException e){
    //         throw new DatabaseOpenException("Could not open the database.", e);
    //     }
    //     try {
    //         try (Statement statement = connection.createStatement()){
    //             statement.execute("PRAGMA foreign_keys = ON");
    //         }
    //         return connection;
    //     } catch (SQLException e){
    //         try {
    //             connection.close();
    //         } catch (SQLException closException){
    //             e.addSuppressed(closException);
    //         }
    //         throw new DatabaseOpenException("Could not configure the database connection.", e);
    //     }
    // }    

}
