package transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Scanner;
import java.util.Optional;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.example.domain.Item;
import com.example.domain.User;
import com.example.infrastructure.AppLogger;
import com.example.infrastructure.Database;
import com.example.repository.ItemRepository;
import com.example.repository.RentalRepository;
import com.example.repository.SchemaInitialiser;
import com.example.repository.UserRepository;
import com.example.service.ItemService;
import com.example.service.RentalService;
import com.example.service.UserService;
import com.example.transport.Transport;
import com.example.transport.exception.TransportException;

public class TransportTest {
    private Connection connection;
    private ItemService itemService;
    private UserService userService;
    private ItemRepository itemRepository;
    private UserRepository userRepository;
    private RentalRepository rentalRepository;
    private RentalService rentalService;
    private Transport transport;
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

            userService = new UserService(userRepository, logger);
            itemService = new ItemService(itemRepository, rentalRepository, logger);
            rentalService = new RentalService(rentalRepository, itemService, userService, logger);

            transport = new Transport(itemService, userService, rentalService, logger);
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

    @Test 
    void testListingItem() throws Exception {
        // Sequence: Username -> List item -> Item name -> Description -> Cost per day -> Exit
        userService.register("Juhan", "password");
        String output = runAuthenticationScript("1\nJuhan\npassword\n" + "1\n" + "car\nAudi\n100\n" + "5\n");
        Optional<Item> item = itemService.findItemByID(1);

        assertEquals("car", item.get().getItemName());
        assertTrue(output.contains("Goodbye!"), output);
    }

    @Test 
    void testViewingItemDetails() throws Exception {
        userService.register("Juhan", "password");
        itemService.createItem(1, "car", "bmw", 50);
        itemService.changeItemStatus(1, "delist", 1);
        itemService.createItem(1, "laptop", "lenovo", 10);
        // Sequence: View my inventory -> Pick item number 1 -> Back to list -> Pick item number 2 -> Back to list -> Back to menu -> Exit
        String input = """
            1
            Juhan
            password
            2
            1
            2
            2
            2
            3
            5
            """;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes());
        System.setIn(inputStream);
        Scanner sc = new Scanner(System.in);
        transport.CLI(connection, sc);

        assertTrue(outputStream.toString().contains("laptop"));
    }

    @Test 
    void testInvalidInput() throws Exception {
        userService.register("Juhan", "password");
        // Sequence: Invalid input -> Invalid String -> Username -> Invalid menu option -> Invalid integer -> Invalid integer -> Invalid integer -> Exit
        String input = """
            1             
            Juhan
            password
            asd
            6
            66666
            5.5
            5
            """;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        System.setErr(new PrintStream(outputStream));
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes());
        System.setIn(inputStream);
        Scanner sc = new Scanner(System.in);
        transport.CLI(connection, sc);

        assertTrue(outputStream.toString().contains("Invalid input, a whole number expected. Try again."));
        assertTrue(outputStream.toString().contains("Invalid choice, pick a number from 1 to 5."));
    }

    @Test 
    void testInvalidInputWhenViewingItemList() throws Exception {
        userService.register("Juhan", "password");
        itemService.createItem(1, "car1", "bmw", 50);
        itemService.createItem(1, "car2", "audi", 10);
        itemService.createItem(1, "car3", "volvo", 50);
        itemService.createItem(1, "car4", "vw", 10);
        itemService.createItem(1, "car5", "kia", 50);
        itemService.createItem(1, "car6", "fiat", 10);
        // Sequence: Number too big -> View List -> Invalid input -> Next page -> Invalid input -> Prev page -> Pick item 1 -> Invalid input -> Back to menu -> Exit
        String input = """
            1
            Juhan
            password
            15151
            2
            8
            6
            0
            2
            1
            8
            7
            5
            """;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes());
        System.setIn(inputStream);
        Scanner sc = new Scanner(System.in);
        transport.CLI(connection, sc);

        assertTrue(outputStream.toString().contains("Invalid choice, pick a number from 1 to "));
        assertTrue(outputStream.toString().contains("Invalid choice"));
    }

    @Test 
    void testEmptyInventory() throws Exception {
        userService.register("Juhan", "password");
        // Sequence: Username -> View inventory -> Exit
        String input = """
            1
            Juhan
            password
            2
            5
            """;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes());
        System.setIn(inputStream);
        Scanner sc = new Scanner(System.in);
        transport.CLI(connection, sc);

        assertTrue(outputStream.toString().contains("No listed items."));
    }

    @Test 
    void relistAndDelistItemInInventory() throws Exception {
        userService.register("Juhan", "password");
        itemService.createItem(1, "car", "bmw", 50);
        itemService.createItem(1, "laptop", "lenovo", 10);
        // Sequence: View my inventory -> Pick item number 2 (laptop) -> Delist -> Pick item number 2 -> Relist -> Back to Menu -> Exit
        String input = """
            1
            Juhan
            password
            2
            2
            1
            2
            1
            3
            5
            """;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes());
        System.setIn(inputStream);
        Scanner sc = new Scanner(System.in);
        transport.CLI(connection, sc);

        assertTrue(outputStream.toString().contains("Item delisted successfully."));
        assertTrue(outputStream.toString().contains("Item relisted successfully."));
    }

     @Test 
    void listItemWithNegativeCostPrintsErr() throws Exception {
        userService.register("Juhan", "password");
        // Sequence: Login -> List item -> Itemname -> Description -> Negative cost -> Exit
        String input = """
            1
            Juhan
            password
            1
            Car
            BMW
            -500
            5
            """;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        System.setErr(new PrintStream(outputStream));
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes());
        System.setIn(inputStream);
        Scanner sc = new Scanner(System.in);
        transport.CLI(connection, sc);

        assertTrue(outputStream.toString().contains("Cost per day cannot be negative."));
    }


    @Test 
    void relistItemWithActiveRentalPrintsError() throws Exception {
        userService.register("Juhan", "password");
        userService.register("name", "password");
        itemService.createItem(1, "car", "bmw", 50);
        rentalService.recordRental(1, 2, 3);
        // Sequence: View my inventory -> Pick item number 1 -> Delist -> Pick item number 1 -> Relist -> Back to Menu -> Exit
        String input = """
            1
            Juhan
            password
            2
            1
            1
            1
            1
            2
            5
            """;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        System.setErr(new PrintStream(outputStream));
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes());
        System.setIn(inputStream);
        Scanner sc = new Scanner(System.in);
        transport.CLI(connection, sc);

        assertTrue(outputStream.toString().contains("Cannot relist an item with an active rental"));
    }

    @Test 
    void testRentingItem() throws Exception {
        userService.register("Juhan", "password");
        userService.register("name", "password");
        itemService.createItem(1, "car1", "bmw", 50);
        itemService.createItem(1, "car2", "audi", 10);
        itemService.createItem(1, "car3", "volvo", 50);
        itemService.createItem(1, "car4", "vw", 10);
        itemService.createItem(1, "car5", "kia", 50);
        itemService.createItem(1, "car6", "fiat", 10);
        // Sequence: Record rental -> Next Page -> Previous Page -> Back to menu -> Record rental -> Item nr 1 -> Renter name -> Days to rent -> Exit
        String input = """
            1
            name
            password
            3
            6
            2
            7
            3
            1
            1
            5
            5
            """;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes());
        System.setIn(inputStream);
        Scanner sc = new Scanner(System.in);
        transport.CLI(connection, sc);

        assertTrue(outputStream.toString().contains("Item rented successfully."));
    }

    @Test 
    void testEmptyRentableList() throws Exception {
        userService.register("Juhan", "password");
        // Sequence: Record rental -> Exit
        String input = """
            1
            Juhan
            password
            3
            5
            """;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes());
        System.setIn(inputStream);
        Scanner sc = new Scanner(System.in);
        transport.CLI(connection, sc);

        assertTrue(outputStream.toString().contains("No items available"));
    }

    @Test 
    void testInvalidInputWhenRentingItem() throws Exception {
        User owner = userService.register("Juhan", "password");
        userService.register("name", "password");
        Item item = itemService.createItem(owner.getId(), "car", "bmw", 50);
        // Sequence: Record rental -> Invalid number 0 -> Record rental -> Invalid number 3 -> Record rental -> Invalid Renter name -> 
        // -> Record rental -> Renter name -> Invalid Days to rent -> Exit
        String output = runAuthenticationScript("1\nname\npassword\n3\n1\n1\n0\n5\n");
        assertTrue(output.contains("Rental duration must be greater than zero."), output);
        assertTrue(output.contains("Goodbye!"), output);
        assertEquals("available", itemService.findItemByID(item.getId()).orElseThrow().getStatus());
        assertFalse(rentalService.hasActiveRental(item.getId()));
    }

    @Test 
    void testConfirmReturnList() throws Exception {
        userService.register("Juhan", "password");
        userService.register("name", "password");
        itemService.createItem(1, "car1", "bmw", 50);
        itemService.createItem(1, "car2", "audi", 10);
        itemService.createItem(1, "car3", "volvo", 50);
        itemService.createItem(1, "car4", "vw", 10);
        itemService.createItem(1, "car5", "kia", 50);
        itemService.createItem(1, "car6", "fiat", 10);
        rentalService.recordRental(1, 2, 3);
        rentalService.recordRental(2, 2, 3);
        rentalService.recordRental(3, 2, 3);
        rentalService.recordRental(4, 2, 3);
        rentalService.recordRental(5, 2, 3);
        rentalService.recordRental(6, 2, 3);
        // Sequence: Menu option 4 -> Next page -> Prev page -> Back to Menu -> Menu option 4 -> Item nr 1 -> Invalid Choice -> Item nr 1 -> 
        // -> Back to List -> Item nr 1 -> Confirm return -> Exit
        String input = """
            1
            Juhan
            password
            4
            6
            2
            7
            4
            1
            3
            1
            2
            1
            1
            5
            """;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes());
        System.setIn(inputStream);
        Scanner sc = new Scanner(System.in);
        transport.CLI(connection, sc);

        assertTrue(outputStream.toString().contains("Item returned successfully.\n"));
    }

    @Test 
    void confirmReturnsThrowsPrintsServiceException() throws Exception {
        userService.register("Juhan", "password");
        userService.register("name", "password");
        itemService.createItem(1, "car", "bmw", 50);
        rentalService.recordRental(1, 2, 3);
        try (Statement st = connection.createStatement()) {
            st.execute("UPDATE listed_items SET status = 'available' WHERE item_id = 1");
        }

        // Sequence: login -> menu 4 -> item 1 -> confirm return -> exit
        String input = "1\nJuhan\npassword\n4\n1\n1\n5\n";

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        System.setErr(new PrintStream(outputStream));
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes());
        System.setIn(inputStream);
        Scanner sc = new Scanner(System.in);
        transport.CLI(connection, sc);

        assertTrue(outputStream.toString().contains("Item is not in a valid state for return"));
    }

    @Test 
    void testEmptyConfirmReturnList() throws Exception {
        userService.register("Juhan", "password");
        // Sequence: Menu option 4 -> Exit
        String input = """
            1
            Juhan
            password
            4
            5
            """;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes());
        System.setIn(inputStream);
        Scanner sc = new Scanner(System.in);
        transport.CLI(connection, sc);

        assertTrue(outputStream.toString().contains("Goodbye!"));
    }

    private String runAuthenticationScript(String input) throws TransportException{
        PrintStream previousOut = System.out;
        PrintStream previousErr = System.err;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try(PrintStream capture = new PrintStream(output)){
            Scanner sc = new Scanner(input);
            System.setOut(capture);
            System.setErr(capture);
            transport.CLI(connection, sc);
        } finally {
            System.setOut(previousOut);
            System.setErr(previousErr);
        }
        return output.toString();
    }

    @ParameterizedTest 
    @CsvSource (delimiter = '|', value = {
        "9 | Invalid choice, pick 1 or 2",
        "1\\nJuhan\\nwrong-password | Invalid username or password",
        "1\\nMari\\npassword | Invalid username or password",
        "2\\nJuhan\\nother-password | The username is already taken",
        "2\\nJuhan\\n | Password cannot be empty"
    })
    void authenticateRecoversFromBadInput(String attempt, String expectedError) throws Exception{
        userService.register("Juhan", "password");
        String output = runAuthenticationScript(attempt.replace("\\n", "\n") + "\n" + "1\nJuhan\npassword\n5\n");

        int error = output.indexOf(expectedError);
        int retry = output.indexOf("Do you have an account?", error + 1);
        int menu = output.indexOf("1) List an item");

        assertTrue(error >= 0, output);
        assertTrue(retry > error, output);
        assertTrue(menu > retry, output);
        assertTrue(output.contains("Goodbye!"), output);
        assertFalse(output.contains("SQLITE_"), output);
    }

    @ParameterizedTest 
    @ValueSource (strings = {"", "1\n", "1\nJuhan\n"})
    void  authenticateReturnsWhenInputEnds(String input) throws Exception{
        String output = runAuthenticationScript(input);
        assertTrue(output.contains("Do you have an account?"));
        assertFalse(output.contains("1) List an item"), output);
    }
    
    @Test 
    void rentalDetailsBackToListReturnsToBrowse() throws Exception{
        User owner = userService.register("name", "password");
        userService.register("Juhan", "password");
        Item item = itemService.createItem(owner.getId(), "Drill", "Electric drill", 10);
        String output = runAuthenticationScript("1\nJuhan\npassword\n3\n1\n2\n2\n5\n");
        int details = output.indexOf("=== Drill ===");
        int browseAgain = output.indexOf("=== Browse listings ===", details + 1);


        assertTrue(details >= 0, output);
        assertTrue(browseAgain > details, output);
        assertFalse(output.contains("Enter the rental duration in days"), output);
        assertTrue(output.contains("Goodbye!"), output);
        assertFalse(rentalService.hasActiveRental(item.getId()));
    }

    @Test 
    void rentalDetailsRejectinvalidChoiceAndLetsUserGoBack() throws Exception{
        User owner = userService.register("name", "password");
        userService.register("Juhan", "password");
        itemService.createItem(owner.getId(), "Drill", "Electric drill", 10);
        String output = runAuthenticationScript("1\nJuhan\npassword\n3\n1\n9\n2\n2\n5\n");
        int details = output.indexOf("=== Drill ===");
        int error = output.indexOf("Invalid choice, pick 1 or 2.", details + 1);
        int browseAgain = output.indexOf("=== Browse listings ===", error + 1);

        assertTrue(details >= 0, output);
        assertTrue(error > details, output);
        assertTrue(browseAgain > error, output);
        assertTrue(output.contains("Goodbye!"), output);
    }
}
