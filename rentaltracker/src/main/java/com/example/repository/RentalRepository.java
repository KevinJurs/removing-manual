package com.example.repository;
import com.example.domain.Rental;
import com.example.infrastructure.AppLogger;
import com.example.repository.exception.ForeignKeyConstraintException;
import com.example.repository.exception.MappingException;
import com.example.repository.exception.NotFoundException;
import com.example.repository.exception.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

public class RentalRepository {
   
    private final Connection connection;
    private final AppLogger logger;
    private static final DateTimeFormatter DATABASE_TIME_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    public RentalRepository(Connection connection, AppLogger logger) {
        this.connection = connection;
        this.logger = logger;
    }

    public Rental createRental(long itemId, long renterId, LocalDateTime endTime)  throws RepositoryException {

        String sql = """
            INSERT INTO rentals ( item_id, renter_id, end_time, status)
            VALUES (?, ?, ?, 'active')
            RETURNING item_id, renter_id, start_time, end_time, status, rental_id, returned_at
        """;

        try (PreparedStatement statement = connection.prepareStatement(sql)){
            statement.setLong(1, itemId);
            statement.setLong(2, renterId);
            statement.setString(3, endTime.format(DATABASE_TIME_FORMAT));

            try (ResultSet results = statement.executeQuery()) {
                results.next();
                return mapRental(results);
            }

    } catch (SQLException e) {
        logger.logDatabaseError(e, "insert", "createRental.rentalRepo");
        if (e instanceof SQLiteException sqLiteException && sqLiteException.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_FOREIGNKEY){
            throw new ForeignKeyConstraintException("Invalid foreign key", e);
        }
        throw new RepositoryException("Could not create rental.", e);        
        }
    }

    private Rental mapRental(ResultSet results) throws MappingException {
        try {
            long rentalId = results.getLong("rental_id");
            long itemId = results.getLong("item_id");
            long renterId = results.getLong("renter_id");
            LocalDateTime startTime = LocalDateTime.parse(results.getString("start_time"), DATABASE_TIME_FORMAT);
            LocalDateTime endTime = LocalDateTime.parse(results.getString("end_time"), DATABASE_TIME_FORMAT);
            String status = results.getString("status");


            String returnedAtText = results.getString("returned_at");

                LocalDateTime returnedAt = null;

            if (returnedAtText != null) {

                returnedAt = LocalDateTime.parse(returnedAtText, DATABASE_TIME_FORMAT);
            }

            return new Rental(rentalId, itemId, renterId, startTime, endTime, returnedAt, status);
        } catch (SQLException | DateTimeParseException e){
            logger.log("ERROR", "mapRental.failed", "mapping_exception");
            throw new MappingException("Could not map database record to rental", e);
        }
    }

    public Optional<Rental> findById(long rentalId) throws RepositoryException{
        String sql = "SELECT * FROM rentals WHERE rental_id = ?";
        Optional<Rental> rental;
        try (PreparedStatement statement = connection.prepareStatement(sql)){
            statement.setLong(1, rentalId);
            try (ResultSet results = statement.executeQuery()){
                if (results.next()){
                    //return  Optional.of(mapRental(results));
                    rental = Optional.of(mapRental(results));
                } else {
                    rental = Optional.empty();
                }
                //return Optional.empty();
            }
        } catch (SQLException e){
            logger.logDatabaseError(e, "query", "findById.rentalRepo");
            throw new RepositoryException("Could not find rental by ID", e);
        }
        return rental;
    }

    public boolean hasActiveRental(long itemId) throws RepositoryException{
        String sql = "SELECT 1 FROM rentals WHERE item_id = ? and status = 'active' LIMIT 1";
        try(PreparedStatement statement = connection.prepareStatement(sql)){
            statement.setLong(1, itemId);
            try (ResultSet results = statement.executeQuery()){
                return results.next();
            }
        } catch (SQLException e){
            logger.logDatabaseError(e, "query", "hasActiveRental.rentalRepo");
            throw new RepositoryException("Could not check active rentals", e);
        }
    }

    public void confirmReturn(long rentalId) throws RepositoryException{
        String sql = "UPDATE rentals SET status = 'closed', returned_at = CURRENT_TIMESTAMP WHERE rental_id = ? AND status = 'active'";
        try (PreparedStatement statement = connection.prepareStatement(sql)){
            statement.setLong(1, rentalId);
            int updatedRows = statement.executeUpdate();
            if (updatedRows == 0){
                logger.log("ERROR", "db.update=failed", "rental_id=" + rentalId + " error=not_found");
                throw new NotFoundException("Active rental not found");
            }
        } catch (SQLException e){
            logger.logDatabaseError(e, "update", "confirmReturn.rentalRepo");
            throw new RepositoryException("Could not confirm rental return", e);
        }
    }

    public List<Rental> findActiveRentals(long ownerId, int offset) throws RepositoryException{
        String sql = """
            SELECT rentals.*
            FROM rentals
            JOIN listed_items ON rentals.item_id = listed_items.item_id
            WHERE listed_items.owner_id = ?
            AND rentals.status = 'active'
            ORDER BY rentals.end_time ASC, rentals.rental_id ASC
            LIMIT 6 OFFSET ?
            """;
            List<Rental> rentals = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)){
            statement.setLong(1, ownerId);
            statement.setInt(2, offset);
            try (ResultSet results = statement.executeQuery()){
                while (results.next()){
                    rentals.add(mapRental(results));
                }
            }
        } catch (SQLException e){
            logger.logDatabaseError(e, "query", "findActiveRentals.rentalRepo");
            throw new RepositoryException("Could not fetch the rental list", e);
        }
        return rentals;
    }

}
