package com.example.repository;

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

import com.example.domain.Item;
import com.example.infrastructure.AppLogger;
import com.example.repository.exception.MappingException;
import com.example.repository.exception.NotFoundException;
import com.example.repository.exception.NotNullConstraintException;
import com.example.repository.exception.RepositoryException;
import com.example.service.ItemService;

public class ItemRepository {
    private static final DateTimeFormatter DATABASE_TIME_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private final Connection connection;
    private final AppLogger logger;

    public ItemRepository(Connection connection, AppLogger logger) {
        this.connection = connection;
        this.logger = logger;
    }
 
    public Item createItem(long ownerId, String itemName, String description, int costPerDay) throws RepositoryException {
        String sql = """
            INSERT INTO listed_items (owner_id, item_name, description, cost_per_day, status)
            VALUES (?, ?, ?, ?, ?) 
            RETURNING item_id, owner_id, item_name, description, cost_per_day, status, created_at
        """;
        try (PreparedStatement statement = connection.prepareStatement(sql)){
            statement.setLong(1, ownerId);
            statement.setString(2, itemName);
            statement.setString(3,description);
            statement.setInt(4, costPerDay);
            statement.setString(5, "available");

        try (ResultSet results = statement.executeQuery()) {
            results.next();
            return mapItem(results);
        }
        } catch (SQLException e) {
            logger.logDatabaseError(e, "insert", "createItem.itemRepo");
            if (e instanceof SQLiteException sqLiteException && sqLiteException.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_NOTNULL){
                throw new NotNullConstraintException("A required item field is missing", e);
            }
             throw new RepositoryException("Could not create item.", e);
        }
    }

    private Item mapItem(ResultSet results) throws MappingException {
        try {
            long id = results.getLong("item_id");
            long ownerId = results.getLong("owner_id");
            String itemName = results.getString("item_name");
            String description = results.getString("description");
            int costPerDay = results.getInt("cost_per_day");
            String status = results.getString("status");
            LocalDateTime createdAt = LocalDateTime.parse(results.getString("created_at"),DATABASE_TIME_FORMAT);
            return new Item(id, ownerId, itemName, description, costPerDay, status, createdAt);
        } catch (SQLException | DateTimeParseException e){
            logger.log("ERROR", "mapItem.failed", "mapping_exception");
            throw new MappingException("Could not map database record to item.", e);
        }
    }

    // Finds item by its ID and returns empty Optional if the item does not exist
    public Optional<Item> findById(long itemId) throws RepositoryException {
    String sql = "SELECT * FROM listed_items WHERE item_id = ?";
    Optional<Item> item;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setLong(1, itemId);
        try (ResultSet results = statement.executeQuery()) {
            if (results.next()) {
                //return Optional.of(mapItem(results));
                item = Optional.of(mapItem(results));
            } else {
                item = Optional.empty();
            }
            //return Optional.empty();
        }
        } catch (SQLException e) {
            logger.logDatabaseError(e, "query", "findById.itemRepo");
            throw new RepositoryException("Could not find item by id", e);
        }
        return item;
    }

    //gets item by ID and throws exception if not found
     public Item getById(long id) throws RepositoryException{
        return findById(id).orElseThrow(() -> new NotFoundException("Item was not found."));
    }

    public List<Item> itemList(long userID, int offset) throws RepositoryException {
        String sql = "SELECT * FROM listed_items WHERE owner_id = ? ORDER BY item_id LIMIT 6 OFFSET ?";
        List<Item> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userID);
            statement.setInt(2, offset);
            try (ResultSet results = statement.executeQuery();) {  
                while (results.next()) {
                    items.add(mapItem(results));
                }
            }
        } catch (SQLException e) {
            logger.logDatabaseError(e, "query", "itemList.itemRepo");
            throw new RepositoryException("Could not fetch the item list.", e);
        }
        return items;
    }

    public List<Item> itemListAvailable(long userID, int offset) throws RepositoryException {
        String sql = "SELECT * FROM listed_items WHERE owner_id <> ? AND status = 'available' ORDER BY item_id LIMIT 6 OFFSET ?";
        List<Item> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userID);
            statement.setInt(2, offset);
            try (ResultSet results = statement.executeQuery();) {  
                while (results.next()) {
                    items.add(mapItem(results));
                }
            }
        } catch (SQLException e) {
            logger.logDatabaseError(e, "query", "ItemListAvailable.itemRepo");
            throw new RepositoryException("Could not fetch the item list.", e);
        }
        return items;
    }
  
    public void changeItemStatus(long ownerID, ItemService.ItemListingState statusChange, long itemID) throws RepositoryException {
        String sql = """
            UPDATE listed_items
            SET status = ? 
            WHERE owner_id = ? AND item_id = ?
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, statusChange.toString());
            statement.setLong(2, ownerID);
            statement.setLong(3, itemID);
            
            int updatedRows = statement.executeUpdate();

            if (updatedRows == 0) {
                logger.log("ERROR", "db.update=failed", "item_id=" + itemID + " error=not_found");
                throw new NotFoundException("Item not found.");
            }

        } catch (SQLException e) {
            logger.logDatabaseError(e, "update", "changeItemStatus.itemRepo");
            throw new RepositoryException("Error while changing the state of an item.", e);
        }
    }
}

