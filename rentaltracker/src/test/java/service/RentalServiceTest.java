package service;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.domain.Item;
import com.example.domain.Rental;
import com.example.domain.User;
import com.example.infrastructure.AppLogger;
import com.example.infrastructure.Database;
import com.example.repository.SchemaInitialiser;
import com.example.repository.ItemRepository;
import com.example.repository.UserRepository;
import com.example.repository.RentalRepository;
import com.example.service.ItemService;
import com.example.service.UserService;
import com.example.service.RentalService;
import com.example.service.exception.ServiceException;

public class RentalServiceTest {


    @TempDir
    Path temporaryDirectory;

    private Connection connection;
    private ItemRepository itemRepository;
    private UserRepository userRepository;
    private RentalRepository rentalRepository;

    private ItemService itemService;
    private UserService userService;
    private RentalService rentalService;

    private LocalDateTime endTime = LocalDateTime.now().plusMonths(1);
    private Path logFile;
    private AppLogger logger;

    @BeforeEach
    void setUp() throws Exception {
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
            userService = new UserService(userRepository, logger);
            rentalService = new RentalService(rentalRepository, itemService, userService, logger);

        } catch (Exception e) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                e.addSuppressed(closeException);
            }
            throw e;
        }
}

    @AfterEach
    void closeConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    //test for non-existing item
    @Test
    void recordRentalRejectsNonExistingItem() {
        assertThrows(ServiceException.class, () -> rentalService.recordRental(999, 1, 3));
}
    //test for successful rental
    @Test 
    void recordRentalCreatesRentalSuccesfully() throws Exception {

        User owner = userRepository.createUser("Jaan", "password");
        User renter = userRepository.createUser("Mari", "password");
        int days = 3;
        Item item = itemService.createItem(owner.getId(), "Drill", "Electric drill", 10);
        Rental rental = rentalService.recordRental(item.getId(), renter.getId(), days);
        Rental storedRental = rentalRepository.findById(rental.getRentalId()).orElseThrow();
        assertNotNull(rental);
        //checks that correct item was rented out
        assertEquals(item.getId(), rental.getItemId());
        //checks that item status changed
        assertEquals("rented", itemService.findItemByID(item.getId()).get().getStatus());

        assertEquals(item.getId(), storedRental.getItemId());
        assertEquals(renter.getId(), storedRental.getRenterId());
        assertEquals(storedRental.getStartTime().plusDays(days), storedRental.getEndTime());
        assertEquals("rented", itemRepository.getById(item.getId()).getStatus());
    }

    //test for unlisted item rejection
    @Test
    void recordRentalRejectsUnlistedItem() throws Exception {

        User owner = userRepository.createUser("Jaan", "password");
        Item item = itemService.createItem(owner.getId(), "Drill", "Electric drill", 10);
        
        //makes status "unlisted"
        itemService.changeItemStatus(owner.getId(), "delist", item.getId());
        
        //makes sure that unlisted item throws ServiseEcxeption
        assertThrows(ServiceException.class, () -> rentalService.recordRental(item.getId(), 2, 3));
}

    //test for item that already has active rental
    @Test
    void recordRentalRejectsItemWithActiveRental() throws Exception {

    
        User owner = userRepository.createUser("Jaan", "password");
        Item item = itemService.createItem(owner.getId(), "Drill", "Electric drill", 10);
        User renter = userRepository.createUser("Mari", "password");

        //create active rental without changing item status
        rentalRepository.createRental(
        item.getId(),
        renter.getId(),
        LocalDateTime.now(ZoneOffset.UTC).plusDays(3));
        
        //check that another rental for same item is rejected
        assertThrows(ServiceException.class, () -> rentalService.recordRental(item.getId(), 2, 2));
}

    //test for repository exception
    @Test
    void recordRentalHandlesRepositoryException() throws Exception {

    
        User owner = userRepository.createUser("Jaan", "password");
        Item item = itemService.createItem(owner.getId(), "Drill", "Electric drill", 10);

        //opens separate connection for the rental repository
        Database database = new Database(temporaryDirectory.resolve("test.db"), logger);
        Connection rentalConnection = database.openConnection();

        //creates rental service using the separate connection
        RentalRepository failingRepository = new RentalRepository(rentalConnection, logger);
        RentalService failingService = new RentalService(failingRepository, itemService, userService, logger);

        //closing only rental repository connection to simulate database failure
        rentalConnection.close();

        //checks that we get ServiceException
        assertThrows(ServiceException.class, () -> failingService.recordRental(item.getId(), 2, 3));
    }

    @Test 
    void recordRentalThrowsServiceExceptionWhenTryingToRentOwnItem() throws Exception{
        User owner = userRepository.createUser("name", "password");
        Item item = itemService.createItem(owner.getId(), "Drill", "Electric drill", 10);
        assertThrowsExactly(ServiceException.class,() -> rentalService.recordRental(item.getId(), owner.getId(), 5));
    }

    @Test 
    void findActiveRentalsReturnsListOfRentals() throws Exception{
        User user = userRepository.createUser("name", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        Item item2 = itemRepository.createItem(user.getId(), "Car", "BMW", 100);
        Rental rental = rentalRepository.createRental(item.getId(), user.getId(), endTime);
        Rental rental2 = rentalRepository.createRental(item2.getId(), user.getId(), endTime.plusMonths(1));
        List<Rental> rentals = rentalService.findActiveRentals(user.getId(), 0);
        assertEquals(2, rentals.size());
        assertEquals(rental.getRentalId(), rentals.get(0).getRentalId());
        assertEquals(rental2.getRentalId(), rentals.get(1).getRentalId());
    }

    @Test 
    void findActiveRentalsWithClosedConnectionThrowsException() throws Exception{
        connection.close();
        assertThrowsExactly(ServiceException.class, () -> rentalService.findActiveRentals(1, 0));
    }

    @Test
    void findRentalByIdReturnsExistingRental() throws Exception{
        User user = userRepository.createUser("name", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        Rental rental = rentalRepository.createRental(item.getId(), user.getId(), endTime);
        Optional<Rental> returnedRental = rentalService.findRentalById(rental.getRentalId());
        assertEquals(rental.getRentalId(), returnedRental.get().getRentalId());
    }

    @Test 
    void findRentalByIfWithClosedConnectionThrowsException() throws Exception{
        connection.close();
        assertThrowsExactly(ServiceException.class, () -> rentalService.findRentalById(1));
    }

    @Test 
    void confirmReturnClosesRentalAndMakesItemAvailable() throws Exception{
        User user = userRepository.createUser("name", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        Rental rental = rentalRepository.createRental(item.getId(), user.getId(), endTime);
        
        itemService.changeItemStatus(user.getId(), "rent", item.getId());
        rentalService.confirmReturn(rental.getRentalId(), user.getId());
        
        Rental updatedRental = rentalRepository.findById(rental.getRentalId()).orElseThrow();
        Item updatedItem = itemRepository.getById(item.getId());

        assertEquals("closed", updatedRental.getStatus());
        assertNotNull(updatedRental.getReturnedAt());
        assertEquals("available", updatedItem.getStatus());
    }

    @Test 
    void confirmReturnKeepsUnlistedItemUnlisted() throws Exception{
        User user = userRepository.createUser("name", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        Rental rental = rentalRepository.createRental(item.getId(), user.getId(), endTime);

        itemService.changeItemStatus(user.getId(), "rent", item.getId());
        itemService.changeItemStatus(user.getId(), "delist", item.getId());
        rentalService.confirmReturn(rental.getRentalId(), user.getId());

        Rental updatedRental = rentalRepository.findById(rental.getRentalId()).orElseThrow();
        Item updatedItem = itemRepository.getById(item.getId());

        assertEquals("closed", updatedRental.getStatus());
        assertNotNull(updatedRental.getReturnedAt());
        assertEquals("unlisted", updatedItem.getStatus());
    }

    @Test 
    void confirmReturnThrowsExceptionForClosedRental() throws Exception{
        User user = userRepository.createUser("name", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        Rental rental = rentalRepository.createRental(item.getId(), user.getId(), endTime);
        rentalRepository.confirmReturn(rental.getRentalId());
        assertThrowsExactly(ServiceException.class, () -> rentalService.confirmReturn(rental.getRentalId(), user.getId()));
    }

    @Test 
    void confirmReturnThrowsExceptionForMissingRental(){
        assertThrowsExactly(ServiceException.class, () -> rentalService.confirmReturn(1, 1));
    }

    @Test 
    void confirmReturnThrowsExceptionForAvailableItem() throws Exception{
        User user = userRepository.createUser("name", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        Rental rental = rentalRepository.createRental(item.getId(), user.getId(), endTime);

        assertThrowsExactly(ServiceException.class, () -> rentalService.confirmReturn(rental.getRentalId(), user.getId()));
        
        Rental updatedRental = rentalRepository.findById(rental.getRentalId()).orElseThrow();
        assertEquals("active", updatedRental.getStatus());
    }

    @Test 
    void confirmReturnWithClosedConnectionThrowsServiceException() throws Exception{
        connection.close();
        assertThrowsExactly(ServiceException.class, () -> rentalService.confirmReturn(1, 1));
    }

    @Test 
    void confirmReturnThrowsExceptionForMissingItem() throws Exception{
        User user = userRepository.createUser("name", "password");
        try(Statement statement = connection.createStatement()){
            statement.execute("PRAGMA foreign_keys = OFF");
        }
        long rentalId;
        try{
            Rental rental = rentalRepository.createRental(999, user.getId(), endTime);
            rentalId = rental.getRentalId();
        } finally {
            try (Statement statement = connection.createStatement()){
                statement.execute("PRAGMA foreign_keys = ON");
            }
        }

        assertThrowsExactly(ServiceException.class, () -> rentalService.confirmReturn(rentalId, user.getId()));
    }

    @Test 
    void hasActiveRentalReturnsTrueForActiveRental() throws Exception{
        User user = userRepository.createUser("name", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        rentalRepository.createRental(item.getId(), user.getId(), endTime);
        assertTrue(rentalService.hasActiveRental(item.getId()));
    }

    @Test 
    void hasActiveRentalReturnsFalseWhenNoActiveRental() throws Exception{
        User user = userRepository.createUser("name", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        assertFalse(rentalService.hasActiveRental(item.getId()));
    }

    @Test 
    void hasActiveRentalWithClosedConnectionThrowsException() throws Exception{
        connection.close();
        assertThrowsExactly(ServiceException.class, () -> rentalService.hasActiveRental(1));
    }

    @Test 
    void renterCannotConfirmReturnofAnotherusersItem() throws Exception{
        User owner = userRepository.createUser("name", "password");
        User renter = userRepository.createUser("renter", "password");
        Item item = itemService.createItem(owner.getId(), "Drill", "Electric drill", 10);
        Rental rental = rentalService.recordRental(item.getId(), renter.getId(), 3);
        assertThrowsExactly(ServiceException.class, () -> rentalService.confirmReturn(rental.getRentalId(), renter.getId()));
        Rental storedRental = rentalRepository.findById(rental.getRentalId()).orElseThrow();
        assertEquals("active", storedRental.getStatus());
        assertNull(storedRental.getReturnedAt());
        assertEquals("rented", itemRepository.getById(item.getId()).getStatus());
    }
}

    
