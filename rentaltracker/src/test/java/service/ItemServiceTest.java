package service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.example.domain.Item;
import com.example.domain.Rental;
import com.example.domain.User;
import com.example.infrastructure.AppLogger;
import com.example.infrastructure.Database;
import com.example.repository.SchemaInitialiser;
import com.example.repository.UserRepository;
import com.example.repository.ItemRepository;
import com.example.repository.RentalRepository;
import com.example.service.ItemService;
import com.example.service.ItemService.ItemListingState;
import com.example.service.exception.EmptyItemnameException;
import com.example.service.exception.ServiceException;

public class ItemServiceTest {
    private Connection connection;
    private ItemService itemService;
    private ItemRepository itemRepository;
    private RentalRepository rentalRepository;
    private UserRepository userRepository;
    private LocalDateTime endTime = LocalDateTime.now().plusMonths(1);
    private Path logFile;
    private AppLogger logger;

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void setUp() throws Exception{
        logFile = temporaryDirectory.resolve("app.log");
        logger = new AppLogger(logFile);
        Database database = new Database(temporaryDirectory.resolve("test.db"), logger);
        SchemaInitialiser initialiser = new SchemaInitialiser();
        connection = database.openConnection();

        try {
            initialiser.initialise(connection);
            itemRepository = new ItemRepository(connection, logger);
            userRepository = new UserRepository(connection, logger);
            rentalRepository = new RentalRepository(connection, logger);
            itemService = new ItemService(itemRepository, rentalRepository, logger);
        } catch (Exception e){
            try {
                connection.close();
            } catch (SQLException closException){
                e.addSuppressed(closException);
            }
            throw e;
        }
    }

    @AfterEach 
    void closeConnection() throws SQLException{
        if (connection != null && !connection.isClosed()){
            connection.close();
        }
    }
    //TODO: should the change status tests be split up more?
    @Test 
    void changeItemStatusFromAvailable() throws Exception {
        User createdUser = userRepository.createUser("Juhan", "password");
        Item drill = itemService.createItem(createdUser.getId(), "Drill", "Electric drill", 10);
        Item pc = itemService.createItem(createdUser.getId(), "PC", "Lenovo", 50);
        Item car = itemService.createItem(createdUser.getId(), "BMW", "black", 100);
        
        itemService.changeItemStatus(createdUser.getId(), "delist", drill.getId());
        itemService.changeItemStatus(createdUser.getId(), "rent", pc.getId());
        Optional<Item> drillResult = itemService.findItemByID(drill.getId());
        Optional<Item> pcResult = itemService.findItemByID(pc.getId());

        assertEquals("unlisted", drillResult.get().getStatus());
        assertEquals("rented", pcResult.get().getStatus());
        assertThrowsExactly(IllegalStateException.class, () -> itemService.changeItemStatus(createdUser.getId(), "relist", car.getId()));
    }

    @Test 
    void changeItemStatusFromRented() throws Exception {
        User createdUser = userRepository.createUser("Juhan", "password");
        Item drill = itemService.createItem(createdUser.getId(), "Drill", "Electric drill", 10);
        Item pc = itemService.createItem(createdUser.getId(), "PC", "Lenovo", 50);
        Item car = itemService.createItem(createdUser.getId(), "BMW", "black", 100);
        // Changing all to rented
        itemService.changeItemStatus(createdUser.getId(), "rent", drill.getId());
        itemService.changeItemStatus(createdUser.getId(), "rent", pc.getId());
        itemService.changeItemStatus(createdUser.getId(), "rent", car.getId());

        itemService.changeItemStatus(createdUser.getId(), "delist", drill.getId());
        //itemService.changeItemStatus(createdUser.getId(), "relist", pc.getId());
        Optional<Item> drillResult = itemService.findItemByID(drill.getId());
        //Optional<Item> pcResult = itemService.findItemByID(pc.getId());

        assertEquals("unlisted", drillResult.get().getStatus());
        //assertEquals("available", pcResult.get().getStatus());
        assertThrowsExactly(IllegalStateException.class, () -> itemService.changeItemStatus(createdUser.getId(), "rent", car.getId()));
    }

    @Test 
    void changeItemStatusFromUnlisted() throws Exception {
        User createdUser = userRepository.createUser("Juhan", "password");
        Item drill = itemService.createItem(createdUser.getId(), "Drill", "Electric drill", 10);
        Item pc = itemService.createItem(createdUser.getId(), "PC", "Lenovo", 50);
        Item car = itemService.createItem(createdUser.getId(), "BMW", "black", 100);
        // Changing all to unlisted
        itemService.changeItemStatus(createdUser.getId(), "delist", drill.getId());
        itemService.changeItemStatus(createdUser.getId(), "delist", pc.getId());
        itemService.changeItemStatus(createdUser.getId(), "delist", car.getId());

        itemService.changeItemStatus(createdUser.getId(), "relist", drill.getId());

        Optional<Item> drillResult = itemService.findItemByID(drill.getId());
        Optional<Item> pcResult = itemService.findItemByID(pc.getId());

        assertEquals("available", drillResult.get().getStatus());
        assertThrowsExactly(IllegalStateException.class, () -> itemService.changeItemStatus(createdUser.getId(), "rent", car.getId()));
        assertThrowsExactly(IllegalStateException.class, () -> itemService.changeItemStatus(createdUser.getId(), "delist", pcResult.get().getId()));
    }

    @Test 
    void changeItemStatusInvalidStateChangeThrowsException() throws Exception {
        User createdUser = userRepository.createUser("Juhan", "password");
        Item drill = itemService.createItem(createdUser.getId(), "Drill", "Electric drill", 10);
        assertThrowsExactly(IllegalStateException.class, () -> itemService.changeItemStatus(createdUser.getId(), "random", drill.getId()));
    }

    @Test
    void changeItemStatusWithclosedConnectionThrowsServiceException() throws Exception{
        connection.close();
        assertThrowsExactly(ServiceException.class, () -> itemService.changeItemStatus(1L, "unlisted", 1));
    }


    // @Test 
    // void validateItemnameStripsWhitespace() throws Exception {
    //     String line = "   car    ";
    //     String validatedLine = itemService.validateItemname(line);
    //     assertEquals("car", validatedLine);
    // }

    @ParameterizedTest 
    @NullAndEmptySource 
    @ValueSource (strings = {"  "})
    void voidCreateItemRejectsBlankDescription(String description) throws Exception{
        User owner = userRepository.createUser("name", "password");
        ServiceException error = assertThrowsExactly(ServiceException.class, () -> itemService.createItem(owner.getId(), "Drill", description, 10));
        assertEquals("Item description cannot be empty.", error.getMessage());
        assertTrue(itemRepository.itemList(owner.getId(), 0).isEmpty()) ;
    }

    @Test 
    void createitemRejectsNegativeCost() throws Exception{
        User owner = userRepository.createUser("name", "password");
        ServiceException error = assertThrowsExactly(ServiceException.class, () -> itemService.createItem(owner.getId(), "Drill", "ElectricDrill", -1));
        assertEquals("Cost per day cannot be negative.", error.getMessage());
        assertTrue(itemRepository.itemList(owner.getId(), 0).isEmpty());
    }

    @Test 
    void testCreatingItem() throws Exception {
        User createdUser = userRepository.createUser("testOwner", "password");
        Item createdItem = itemService.createItem(createdUser.getId(), "Drill", "Electric drill", 10);

        assertEquals("Drill", createdItem.getItemName());
        assertEquals("Electric drill", createdItem.getDescription());
        assertEquals(10, createdItem.getCostPerDay());
        assertEquals("available", itemRepository.getById(createdItem.getId()).getStatus());
    }

    @Test
    void createItemWithClosedConnectionThrowsRepositoryException() throws Exception{
        connection.close();
        assertThrowsExactly(ServiceException.class, () -> itemService.createItem(1, "Drill", "Electric drill", 10));
    }

    @Test
    void getAvailableItemsWithclosedConnectionThrowsServiceException() throws Exception{
        connection.close();
        assertThrowsExactly(ServiceException.class, () -> itemService.getAvailableItems(1,1));
    }

    @Test 
    void getAvailableItems() throws Exception {
        User createdUser = userRepository.createUser("Juhan", "password");
        User secondUser = userRepository.createUser("name", "password");
        long userID = secondUser.getId();
        itemService.createItem(userID, "Drill", "Electric drill", 10);
        itemService.createItem(userID, "Car", "BMW", 100);
        itemService.createItem(userID, "PC", "Dell", 25);
        itemService.changeItemStatus(userID, "delist", 1);
        List<Item> items = itemService.getAvailableItems(1, 1);

        assertEquals("Car", items.get(0).getItemName());
        assertEquals("PC", items.get(1).getItemName());
        assertEquals(2, items.size());
    }

    @Test 
    void getAvailableItemsPageNr2() throws Exception {
        User createdUser = userRepository.createUser("Juhan", "password");
        User secondUser = userRepository.createUser("name", "password");
        long userID = secondUser.getId();
        itemService.createItem(userID, "car1", "blue", 10);
        itemService.createItem(userID, "car2", "black", 100);
        itemService.createItem(userID, "car3", "yellow", 25);
        itemService.createItem(userID, "car4", "green", 10);
        itemService.createItem(userID, "car5", "white", 100);
        itemService.createItem(userID, "car6", "violet", 25);
        List<Item> items = itemService.getAvailableItems(1, 2);

        assertEquals("car6", items.get(0).getItemName());
        assertEquals(1, items.size());
    }

    @Test 
    void getItemList() throws Exception {
        User createdUser = userRepository.createUser("Juhan", "password");
        long userID = createdUser.getId();
        itemService.createItem(userID, "Drill", "Electric drill", 10);
        itemService.createItem(userID, "Car", "BMW", 100);
        List<Item> items = itemService.itemListWithOffset(userID,1);

        assertEquals("Drill", items.get(0).getItemName());
        assertEquals("Car", items.get(1).getItemName());
    }

    @Test
    void getItemListWithclosedConnectionThrowsServiceException() throws Exception{
        connection.close();
        assertThrowsExactly(ServiceException.class, () -> itemService.itemListWithOffset(1,1));
    }

    @Test
    void getItemListOffsetWithclosedConnectionThrowsServiceException() throws Exception{
        connection.close();
        assertThrowsExactly(ServiceException.class, () -> itemService.itemListWithOffset(1,1));
    }

    @Test
    void findItemByIDWithclosedConnectionThrowsServiceException() throws Exception{
        connection.close();
        assertThrowsExactly(ServiceException.class, () -> itemService.findItemByID(1));
    }

    @Test 
    void returningRentedItemMakesItAvailable(){
        assertEquals(ItemListingState.available, ItemService.validStateChange("return", ItemListingState.rented));
    }

    @Test 
    void returningUnlistedItemKeepsItUnlisted(){
        assertEquals(ItemListingState.unlisted, ItemService.validStateChange("return", ItemListingState.unlisted));
    }

    @Test
    void returningAvailableItemThrowsException(){
        assertThrowsExactly(IllegalStateException.class, () -> ItemService.validStateChange("return", ItemListingState.available));
    }

    @Test 
    void relistingRentedItemThrowsException(){
        assertThrowsExactly(IllegalStateException.class, () -> ItemService.validStateChange("relist", ItemListingState.rented));
    }

    @Test 
    void cannotRelistUnlistedItemWithActiveRental() throws Exception{
        User user = userRepository.createUser("name", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        rentalRepository.createRental(item.getId(), user.getId(), endTime);
        itemService.changeItemStatus(user.getId(), "delist", item.getId());
        assertThrowsExactly(ServiceException.class, () -> itemService.changeItemStatus(user.getId(), "relist", item.getId()));
        assertEquals("unlisted", itemRepository.getById(item.getId()).getStatus());
    }

    @Test
    void canRelistUnlistedItemAfterRentalClosed() throws Exception{
        User user = userRepository.createUser("name", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        Rental rental = rentalRepository.createRental(item.getId(), user.getId(), endTime);
        itemService.changeItemStatus(user.getId(), "delist", item.getId());
        rentalRepository.confirmReturn(rental.getRentalId());
        itemService.changeItemStatus(user.getId(), "relist", item.getId());
        assertEquals("available", itemRepository.getById(item.getId()).getStatus());
    }

    @ParameterizedTest 
    @CsvSource({
        "delist, available",
        "relist, unlisted"
    })
    void anotherUserCannotChangeItemListing(String action, String initialStatus) throws Exception{
        User owner = userRepository.createUser("name", "password");
        User notOwner = userRepository.createUser("Juhan", "password");
        Item item = itemService.createItem(owner.getId(), "Drill", "Electric drill", 10);
        if ("unlisted".equals(initialStatus)){
            itemService.changeItemStatus(owner.getId(), "delist", item.getId());
        }
        ServiceException error = assertThrowsExactly(ServiceException.class, () -> itemService.changeItemStatus(notOwner.getId(), action, item.getId()));
        assertEquals("You can only change the status of your own items.", error.getMessage());
        assertEquals(initialStatus, itemRepository.getById(item.getId()).getStatus());
    }

}
