package repository;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import com.example.domain.Item;
import com.example.domain.User;
import com.example.infrastructure.AppLogger;
import com.example.infrastructure.Database;
import com.example.repository.ItemRepository;
import com.example.repository.SchemaInitialiser;
import com.example.repository.UserRepository;
import com.example.repository.exception.MappingException;
import com.example.repository.exception.NotFoundException;
import com.example.repository.exception.NotNullConstraintException;
import com.example.repository.exception.RepositoryException;
import com.example.service.ItemService;
import com.example.service.ItemService.ItemListingState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ItemRepositoryTest {

    // creates temp directory for the test database
    @TempDir
    Path tempDirectory;
    private Connection connection;
    private ItemRepository itemRepository;
    private UserRepository userRepository;
    private Path logFile;
    private AppLogger logger;

    //for running
    @BeforeEach 
    void setUp() throws Exception {
        logFile = tempDirectory.resolve("app.log");
        logger = new AppLogger(logFile);
        Database database = new Database(tempDirectory.resolve("test.db"), logger);
        SchemaInitialiser initialiser = new SchemaInitialiser();
        connection = database.openConnection();

        try {
            initialiser.initialise(connection);
            itemRepository = new ItemRepository(connection, logger);
            userRepository = new UserRepository(connection, logger);
        } catch (Exception e){
            try {
                connection.close();
            } catch (SQLException closeException){
                e.addSuppressed(closeException);
            }
            throw e;
        }
    }

    //for connection closing
    @AfterEach 
    void closeConnection() throws SQLException{
        if (connection != null && !connection.isClosed()){
            connection.close();
        }
    }

    @Test 
    //tests if created item returns item with correct name
    void createItemStoresItemInDatabase() throws Exception {
        User createdUser = userRepository.createUser("testOwner", "password");
        Item createdItem = itemRepository.createItem(createdUser.getId(), "Drill", "Electric drill", 10);

        assertEquals("Drill", createdItem.getItemName());
        assertEquals("Electric drill", createdItem.getDescription());
        assertEquals(10, createdItem.getCostPerDay());
    }

    @Test
    void createItemWithclosedConnectionThrowsRepositoryException() throws Exception{
        connection.close();
        RepositoryException exception = assertThrowsExactly(RepositoryException.class, () -> itemRepository.createItem(1, "Drill", "Electric drill", 10));
        assertInstanceOf(SQLException.class, exception.getCause());
    }

    @Test
    void invalidTimestampThrowsMappingException() throws Exception{
        User createdUser = userRepository.createUser("name", "password");
        Item createdItem = itemRepository.createItem(createdUser.getId(), "Drill", "Electric drill", 10);
        String sql = "UPDATE listed_items SET created_at = ? WHERE owner_id = ?";
        try(PreparedStatement statement = connection.prepareStatement(sql)){
            statement.setString(1, "invalid timestamp");
            statement.setLong(2, createdUser.getId());
            assertEquals(1, statement.executeUpdate());
        }
        MappingException exception = assertThrowsExactly(MappingException.class, () -> itemRepository.findById(createdItem.getId()));
        assertInstanceOf(DateTimeParseException.class, exception.getCause());
    }

    @Test 
    void getListOfItems() throws Exception {
        User createdUser = userRepository.createUser("Juhan", "password");
        User secondUser = userRepository.createUser("name", "password");
        long userID = createdUser.getId();
        itemRepository.createItem(userID, "Drill", "Electric drill", 10);
        itemRepository.createItem(userID, "Car", "BMW", 100);
        itemRepository.createItem(userID, "PC", "Dell", 25);
        itemRepository.createItem(secondUser.getId(), "whatever", "whatever", 10);
        List<Item> items = itemRepository.itemList(userID,0);

        assertEquals("Drill", items.get(0).getItemName());
        assertEquals("Car", items.get(1).getItemName());
        assertEquals("PC", items.get(2).getItemName());
        assertEquals(3, items.size());
    }

    @Test
    void findByIdReturnsExistingItem() throws Exception {
        User owner = userRepository.createUser("testOwner", "password");
        Item createdItem = itemRepository.createItem(
            owner.getId(), "Drill", "Electric drill", 10
        );
    
        Optional<Item> foundItem = itemRepository.findById(createdItem.getId());
    
        assertTrue(foundItem.isPresent());
        assertEquals(createdItem.getId(), foundItem.get().getId());
        assertEquals("Drill", foundItem.get().getItemName());
    }

    @Test 
    void findByIdReturnsEmptyWhenItemDoesNotExist() throws Exception {
        Optional<Item> foundItem = itemRepository.findById(999);
        assertTrue(foundItem.isEmpty());
    }

    //checks that getByID returns correct item when ID exists
    @Test 
    void getByIdReturnsExistingItem() throws Exception {
        User owner = userRepository.createUser("testOwner", "password");
        Item createdItem = itemRepository.createItem(
            owner.getId(), "Drill", "Electric drill", 10);

        Item returnedItem = itemRepository.getById(createdItem.getId());

        assertEquals(createdItem.getId(), returnedItem.getId());
        assertEquals(createdItem.getItemName(), returnedItem.getItemName());
        assertEquals(createdItem.getCreatedAt(), returnedItem.getCreatedAt());
    }

    @Test 
    void getByIdThrowsNotFoundException() {
        assertThrows(NotFoundException.class, () -> itemRepository.getById(999));
    }

    @Test 
    void negativeCostThrowsRepositoryExceptionWhenCreatingItem() {
        assertThrows(RepositoryException.class, () -> itemRepository.createItem(1, "Drill", "Electric", -10));
    }
  
    @Test
    void changeItemStatusWithclosedConnectionThrowsRepositoryException() throws Exception{
        ItemListingState availableState = ItemListingState.valueOf("available");
        ItemService.ItemListingState state = ItemService.validStateChange("rent", availableState);
        connection.close();
        assertThrowsExactly(RepositoryException.class, () -> itemRepository.changeItemStatus(1L, state, 1L));
    }
    // Checks that changing the status of non-existing item throws exception
    @Test
    void changeItemStatusWithInvalidItemIdThrowsNotFoundException() {
        ItemListingState state = ItemListingState.rented;

        assertThrows(NotFoundException.class, () -> itemRepository.changeItemStatus(1L, state, 999L));
    }
    
    @Test 
    void changeItemStatusPersistsRentedStatus() throws Exception{
        User user = userRepository.createUser("name", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        itemRepository.changeItemStatus(user.getId(), ItemListingState.rented, item.getId());

        String sql = "SELECT status FROM listed_items WHERE item_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)){
            statement.setLong(1, item.getId());
            try (ResultSet results = statement.executeQuery()){
                assertTrue(results.next());
                assertEquals("rented", results.getString("status"));
            }
        }
    }
    
    @Test
    void itemListWithOffsetWithClosedConnectionThrowsException() throws Exception {
        connection.close();

        RepositoryException exception = assertThrows(RepositoryException.class, () -> itemRepository.itemList(1L, 1));
        assertInstanceOf(SQLException.class, exception.getCause());
    }
    
    @Test 
    void createItemWithoutNameThrowsException() throws Exception{
        User user = userRepository.createUser("name", "password");
        assertThrowsExactly(NotNullConstraintException.class, () -> itemRepository.createItem(user.getId(), null, "Electric drill", 10));
    }

    @Test 
    void databaseRejectsInvalidItemStatus() throws Exception{
        User user = userRepository.createUser("name", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        String sql = "UPDATE listed_items SET status = ? WHERE item_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)){
            statement.setString(1, "invalid");
            statement.setLong(2, item.getId());
            SQLiteException exception = assertThrowsExactly(SQLiteException.class, statement::executeUpdate);
            assertEquals(SQLiteErrorCode.SQLITE_CONSTRAINT_CHECK, exception.getResultCode());
        }
        assertEquals("available", itemRepository.getById(item.getId()).getStatus());
    }


}
