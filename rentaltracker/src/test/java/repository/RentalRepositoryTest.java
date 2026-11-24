package repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteException;

import com.example.domain.Item;
import com.example.domain.Rental;
import com.example.domain.User;
import com.example.infrastructure.AppLogger;
import com.example.infrastructure.Database;
import com.example.repository.ItemRepository;
import com.example.repository.RentalRepository;
import com.example.repository.SchemaInitialiser;
import com.example.repository.UserRepository;
import com.example.repository.exception.ForeignKeyConstraintException;
import com.example.repository.exception.MappingException;
import com.example.repository.exception.NotFoundException;
import com.example.repository.exception.RepositoryException;

public class RentalRepositoryTest {
    
    @TempDir
        Path tempDirectory;
        private Connection connection;
        private UserRepository userRepository;
        private ItemRepository itemRepository;
        private RentalRepository rentalRepository;
        private LocalDateTime endTime = LocalDateTime.now().plusMonths(1);
        private Path logFile;
        private AppLogger logger;

    @BeforeEach 
        void setUp() throws Exception {
            logFile = tempDirectory.resolve("app.log");
            logger = new AppLogger(logFile);
            Database database = new Database(tempDirectory.resolve("test.db"), logger);
            SchemaInitialiser initialiser = new SchemaInitialiser();
            connection = database.openConnection();

        try {
            initialiser.initialise(connection);
            userRepository = new UserRepository(connection, logger);
            rentalRepository = new RentalRepository(connection, logger);
            itemRepository = new ItemRepository(connection, logger);

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
    void createRentalThrowsExceptionWithClosedConnection() throws Exception{
        User user = userRepository.createUser("name", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        connection.close();
        assertThrowsExactly(RepositoryException.class, () -> rentalRepository.createRental(item.getId(), user.getId(), endTime));
    }

    @Test 
    void invalidTimestampThrowsMappingException() throws Exception{
        User user = userRepository.createUser("name", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        Rental rental = rentalRepository.createRental(item.getId(), user.getId(), endTime);
        String sql = "UPDATE rentals SET start_time = ? WHERE rental_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)){
            statement.setString(1, "invalid timestamp");
            statement.setLong(2, rental.getRentalId());
            assertEquals(1, statement.executeUpdate());
        }
        MappingException exception = assertThrowsExactly(MappingException.class, () -> rentalRepository.findById(rental.getRentalId()));
        assertInstanceOf(DateTimeParseException.class, exception.getCause());
    }


    @Test 
    void findByIdReturnsExistingRental()throws Exception{
        User user = userRepository.createUser("name", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        Rental rental = rentalRepository.createRental(item.getId(), user.getId(), endTime);
        Optional<Rental> foundRental = rentalRepository.findById(1);
        assertFalse(foundRental.isEmpty());
        assertEquals(rental.getRentalId(), foundRental.get().getRentalId());
        assertEquals(rental.getItemId(), foundRental.get().getItemId());
        assertEquals(rental.getRenterId(), foundRental.get().getRenterId());
        assertEquals(rental.getStartTime(),foundRental.get().getStartTime());
        assertEquals(rental.getEndTime(), foundRental.get().getEndTime());
        assertEquals(rental.getStatus(), foundRental.get().getStatus());
        assertEquals(rental.getReturnedAt(), foundRental.get().getReturnedAt());        
    }

    @Test 
    void findByIdReturnsEmptyForMissingRental() throws Exception{
        Optional<Rental> foundRental = rentalRepository.findById(1);
        assertTrue(foundRental.isEmpty());
    }

    @Test 
    void findByIdThrowsExceptionWithClosedConnection() throws Exception{
        connection.close();
        assertThrowsExactly(RepositoryException.class, () -> rentalRepository.findById(1));
    }

    @Test 
    void confirmReturnClosesActiveRental() throws Exception{
        User user = userRepository.createUser("name", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        Rental rental = rentalRepository.createRental(item.getId(), user.getId(), endTime);
        Optional<Rental> foundRental = rentalRepository.findById(rental.getRentalId());
        assertEquals("active", foundRental.get().getStatus());
        rentalRepository.confirmReturn(rental.getRentalId());
        foundRental = rentalRepository.findById(rental.getRentalId());
        assertEquals("closed", foundRental.get().getStatus());
        assertNotNull(foundRental.get().getReturnedAt());
    }

    @Test 
    void confirmReturnThrowsExceptionForClosedRental()throws Exception{
        User user = userRepository.createUser("name", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        Rental rental = rentalRepository.createRental(item.getId(), user.getId(), endTime);
        rentalRepository.confirmReturn(rental.getRentalId());
        assertThrowsExactly(NotFoundException.class, () -> rentalRepository.confirmReturn(rental.getRentalId()));
    }

    @Test 
    void confirmReturnThrowsExceptionForMissingRental(){
        assertThrowsExactly(NotFoundException.class, () -> rentalRepository.confirmReturn(1));
    }

    @Test 
    void confirmReturnWithClosedConnectionThrowsException() throws Exception{
        connection.close();
        assertThrowsExactly(RepositoryException.class, () -> rentalRepository.confirmReturn(1));
    }
    
    @Test 
    void hasActiveRentalReturnsTrueForActiveRental() throws Exception{
        User user = userRepository.createUser("name", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        Rental rental = rentalRepository.createRental(item.getId(), user.getId(), endTime);
        assertTrue(rentalRepository.hasActiveRental(rental.getItemId()));
    }

    @Test 
    void hasActiveRentalReturnsFalseForClosedRental() throws Exception{
        User user = userRepository.createUser("name", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        Rental rental = rentalRepository.createRental(item.getId(), user.getId(), endTime);
        rentalRepository.confirmReturn(rental.getRentalId());
        assertFalse(rentalRepository.hasActiveRental(rental.getItemId()));
    }

    @Test
    void hasActiveRentalReturnsFalseforMissingRental() throws Exception{
        assertFalse(rentalRepository.hasActiveRental(1));
    }

    @Test 
    void hasActiveRentalWithClosedConnectionThrowsException() throws Exception{
        connection.close();
        assertThrowsExactly(RepositoryException.class, () -> rentalRepository.hasActiveRental(1));
    }
    @Test 
    //checks that new rental is created with correct details
    void createRentalRetunrsCorrectRental() throws Exception {
    
    User user = userRepository.createUser("name", "password");
    Item item = itemRepository.createItem(user.getId(), "Drill", "electric drill", 10);
    LocalDateTime endTime = LocalDateTime.of(2026, 11, 5, 12, 0);
    Rental rental = rentalRepository.createRental(item.getId(), user.getId(), endTime);

    assertEquals(item.getId(), rental.getItemId());
    assertEquals(user.getId(), rental.getRenterId());
    assertEquals(endTime, rental.getEndTime());
    assertNull(rental.getReturnedAt());
    assertEquals("active", rental.getStatus());
    assertNotNull(rental.getStartTime());
    assertTrue(rental.getRentalId() > 0);

    }

    @Test 
    void findActiveRentalsReturnsOnlyOwnersActiveRentals() throws Exception{
        User user = userRepository.createUser("name", "password");
        User user2 = userRepository.createUser("name2", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        Item item2 = itemRepository.createItem(user.getId(), "Car", "BMW", 100);
        Item item3 = itemRepository.createItem(user2.getId(), "PC", "Dell", 25);
        Rental rental = rentalRepository.createRental(item.getId(), user.getId(), endTime);
        Rental rental2 = rentalRepository.createRental(item2.getId(), user.getId(), endTime);
        rentalRepository.createRental(item3.getId(), user2.getId(), endTime);
        rentalRepository.confirmReturn(rental2.getRentalId());
        List<Rental> rentals = rentalRepository.findActiveRentals(user.getId(), 0);
        assertEquals(1, rentals.size());
        assertEquals(rental.getRentalId(), rentals.get(0).getRentalId());
    }

    @Test 
    void findActiveRentalsReturnsEmptyListWhenNoneExist() throws Exception{
        assertTrue(rentalRepository.findActiveRentals(1, 0).isEmpty());
    }

    @Test 
    void findActiveRentalsOrdersByEndTime() throws Exception{
        User user = userRepository.createUser("name", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        Item item2 = itemRepository.createItem(user.getId(), "Car", "BMW", 100);
        Item item3 = itemRepository.createItem(user.getId(), "PC", "Dell", 25);
        Rental rental = rentalRepository.createRental(item.getId(), user.getId(), endTime.plusMonths(2));
        Rental rental2 = rentalRepository.createRental(item2.getId(), user.getId(), endTime);
        Rental rental3 = rentalRepository.createRental(item3.getId(), user.getId(), endTime.plusMonths(1));
        List<Rental> rentals = rentalRepository.findActiveRentals(user.getId(), 0);
        assertEquals(rental2.getRentalId(), rentals.get(0).getRentalId());
        assertEquals(rental3.getRentalId(), rentals.get(1).getRentalId());
        assertEquals(rental.getRentalId(), rentals.get(2).getRentalId());
    }

    @Test 
    void findActiveRentalsWithClosedConnectionThrowsRepositoryException() throws Exception{
        connection.close();
        assertThrowsExactly(RepositoryException.class, () -> rentalRepository.findActiveRentals(1, 0));
    }

    @Test 
    void createRentalForMissingItemThrowsForeignkeyConstraintException() throws Exception{
        User user = userRepository.createUser("name", "password");
        assertThrowsExactly(ForeignKeyConstraintException.class, () -> rentalRepository.createRental(999L, user.getId(), endTime));
    }

    @Test 
    void createRentalWithMissingTableThrowsRepositoryException() throws Exception{
        User user = userRepository.createUser("name", "password");
        Item item = itemRepository.createItem(user.getId(), "Drill", "Electric drill", 10);
        try (Statement statement = connection.createStatement()){
            statement.executeUpdate("ALTER TABLE rentals RENAME TO unavailable_rentals");
        }

        RepositoryException exception = assertThrowsExactly(RepositoryException.class, () -> rentalRepository.createRental(item.getId(), user.getId(), endTime));
        assertInstanceOf(SQLiteException.class, exception.getCause());
    }
}
