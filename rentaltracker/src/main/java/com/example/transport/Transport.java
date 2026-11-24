package com.example.transport;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import com.example.domain.User;
import com.example.domain.Item;
import com.example.domain.Rental;
import com.example.service.ItemService;
import com.example.service.RentalService;
import com.example.service.UserService;
import com.example.service.exception.ServiceException;
import com.example.transport.exception.TransportException;
import com.example.infrastructure.AppLogger;

public class Transport {
    private final ItemService itemService;
    private final UserService userService;
    private final RentalService rentalService;
    private final AppLogger logger;
    private static final int NEXT_PAGE = -1;
    private static final int PREV_PAGE = -2;
    private static final int BACK_TO_MENU = -3;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public Transport(ItemService itemService, UserService userService, RentalService rentalService, AppLogger logger){
        this.itemService = itemService;
        this.userService = userService;
        this.rentalService = rentalService;
        this.logger = logger;
    }

    public void CLI(Connection connection, Scanner sc) throws TransportException {
        Optional<User> user = authenticate(sc);
        if(user.isEmpty()){
            return;
        }
        long userId = user.get().getId();
        try {
            boolean menuLoop = true;
            while (menuLoop) {
                System.out.println("\n=== Rental tracker ===");
                System.out.println("1) List an item");
                System.out.println("2) View my inventory");
                System.out.println("3) Browse listings");
                System.out.println("4) Confirm a return");
                System.out.println("5) Exit");
                int menuInput = validateInputInt(sc);

                switch (menuInput) {
                    case 1 -> listItem(sc, userId);
                    case 2 -> viewListedItems(sc, userId);
                    case 3 -> rentItem(sc, userId);
                    case 4 -> returnItem(sc, userId);
                    case 5 -> {
                        System.out.println("Goodbye!\n");
                        menuLoop = false;
                    }
                    default ->
                        System.err.println("Invalid choice, pick a number from 1 to 5.");
                }
            }
        } catch (ServiceException e) {
            throw new TransportException(e.getMessage(), e);
        }
    }

    private Optional<User> authenticate(Scanner sc){
        while(true){
            System.out.println("\n=== Rental tracker ===");
            System.out.println("Do you have an account?");
            System.out.println("1) Log in");
            System.out.println("2) Register");
            System.out.print("> ");
            if (!sc.hasNextLine()){
                return Optional.empty();
            }
            String choice = sc.nextLine().strip();
            if (!choice.equals("1") && !choice.equals("2")){
                System.err.print("Invalid choice, pick 1 or 2");
                continue;
            }
            System.out.println("Please enter your username.");
            System.out.print("> ");
            if(!sc.hasNextLine()){
                return Optional.empty();
            }
            String username = sc.nextLine();
            System.out.println("Please enter your password.");
            System.out.print("> ");
            if(!sc.hasNextLine()){
                return Optional.empty();
            }
            String password = sc.nextLine();
            try{
                if (choice.equals("2")){
                    userService.register(username, password);
                    System.out.println("Account created.");
                    continue;
                }
                Optional <User> user = userService.login(username, password);
                if(user.isPresent()) {
                    return user;
                }
                System.err.println("Invalid username or password");
            } catch (ServiceException e){
                System.err.println(e.getMessage());
            }
        }
    }

    // private String validateInputString(Scanner sc) {
    //     System.out.print("> ");
    //     boolean isValidInput = false;
    //     String line = "";
    //     while (!isValidInput) {
    //         line = sc.nextLine().trim();
    //         if (line.isEmpty()) {
    //             System.err.println("Invalid input. Try again.");
    //             System.out.print("> ");
    //         } else {
    //             isValidInput = true;
    //         }
    //     }
    //     return line;
    // }

    private int validateInputInt(Scanner sc) {
        System.out.print("> ");
        boolean isValidInput = false;
        int i = 0;
        while (!isValidInput) {
            String line = sc.nextLine().trim();
            try {
                i = Integer.parseInt(line);
                isValidInput = true;
            } catch (NumberFormatException e) {
                logger.log("WARN", "input.invalid", "reason=not_integer");
                System.err.println("Invalid input, a whole number expected. Try again.");
                System.out.print("> ");
            }
        }
        return i;
    }

    private void listItem(Scanner sc, long userId) throws ServiceException {
        System.out.println("Enter the item name:");
        String name = sc.nextLine().trim();
        System.out.println("Enter the item description: ");
        String description = sc.nextLine().trim();
        System.out.println("Enter the item cost per day: ");
        int cost = validateInputInt(sc);
        try {
            itemService.createItem(userId, name, description, cost);
            System.out.println("Item listed successfully.");
        } catch (ServiceException e) {
            System.err.println(e.getMessage());
        }
        
    }

    private void viewListedItems(Scanner sc, long userId) throws ServiceException {
        boolean inventoryLoop = true;
        int pageNr = 1;
        while (inventoryLoop) {
            List<Item> inventory = itemService.itemListWithOffset(userId, pageNr);
            System.out.println("\n=== My inventory ===");
            if (inventory.size() == 0) {
                System.out.println("No listed items.");
                return;
            }
            int lineNr = 1;
            int listSize = inventory.size();
            for (Item entry : inventory) {
                // Prints the first 5 entries
                if (lineNr == 6) {
                    break;
                }
                // Formats the print line to fix alignment
                System.out.printf("%d) %-8s %s%n", lineNr, entry.getItemName(), entry.getStatus());
                lineNr++;
            }
            int input = pagePagination(sc, listSize, pageNr, lineNr);
            if (input == NEXT_PAGE){
                pageNr += 1;
            } else if (input == PREV_PAGE) {
                pageNr -= 1;
            } else if (input == BACK_TO_MENU) {
                inventoryLoop = false;
            } else {
                viewItemDetails(sc, inventory, input, userId);
            }
        }
    }

    private int pagePagination(Scanner sc, int listSize, int pageNr, int lineNr) {
        int nextPageIndex = -1;
        int prevPageIndex = -1;
        if (itemService.hasNextPage(listSize)) {
            System.out.println(lineNr + ") Next page");
            nextPageIndex = lineNr;
            lineNr++;
        }
        if (itemService.hasPrevPage(pageNr)) {
            System.out.println(lineNr + ") Previous page");
            prevPageIndex = lineNr;
            lineNr++;
        }
        System.out.println(lineNr + ") Back to menu");
        while (true) {
            int input = validateInputInt(sc);
            if (itemService.hasNextPage(listSize) && input == nextPageIndex){
                return NEXT_PAGE;
            } else if (itemService.hasPrevPage(pageNr) && input == prevPageIndex) {
                return PREV_PAGE;
            } else if (input == lineNr) {
                return BACK_TO_MENU;
            } else if (input > lineNr || input < 1) {
                System.out.println("Invalid choice, pick a number from " + 1 + " to " + lineNr);
            } else {
                return input;
            }
        }
    }

    private void viewItemDetails(Scanner sc, List<Item> inventory, int itemListIndex, long userId) throws ServiceException {
        Item item = inventory.get(itemListIndex-1);
        Optional<User> owner = userService.findUser(item.getOwnerId());
        String status = item.getStatus();
        String listed = item.getCreatedAt().atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.systemDefault()).format(formatter);
        System.out.println("\n=== " + item.getItemName() + " ===");
        System.out.println("description     " + item.getDescription());
        System.out.println("cost per day    " + item.getCostPerDay());
        System.out.println("status          " + status);
        System.out.println("owner           " + owner.get().getUsername());
        System.out.println("listed          " + listed);
        boolean relist = true;
        if (status.equals("available") || status.equals("rented")){
            System.out.println("1) Delist");
            relist = false;
        } else {
            System.out.println("1) Relist");
        }
        System.out.println("2) Back to list");
        int input = validateInputInt(sc);
        switch (input) {
            case 1:
                String action = relist ? "relist" : "delist";
                try {
                    itemService.changeItemStatus(userId, action, item.getId());
                    System.out.println("Item " + action + "ed successfully.");
                } catch (ServiceException e) {
                    System.err.println(e.getMessage());
                }
                break;
            case 2:
                break;
            default: 
                System.out.println("Invalid choice.");
                break;
        }
    }

    private void rentItem(Scanner sc, long userId) throws ServiceException {
        int pageNr = 1;
        while (true) {
            System.out.println("\n=== Browse listings ===");
            List<Item> rentables = itemService.getAvailableItems(userId, pageNr);
            if (rentables.isEmpty()){
                System.out.println("No items available");
                return;
            }
            int lineNr = 1;
            for (Item entry : rentables) {
                if (lineNr == 6) {
                    break;
                }
                Optional<User> owner = userService.findUser(entry.getOwnerId());
                System.out.printf("%d) %-8s %s %s%n", lineNr, entry.getItemName(), "costs " + entry.getCostPerDay() + "\\day", owner.get().getUsername());
                lineNr++;
            }

            int input = pagePagination(sc, rentables.size(), pageNr, lineNr);
            if (input == NEXT_PAGE){
                pageNr += 1;
            } else if (input == PREV_PAGE) {
                pageNr -= 1;
            } else if (input == BACK_TO_MENU) {
                return;
            } else {
                Item item = rentables.get(input -1);
                if (viewRentalItemDetails(sc, item, userId)){
                    return;
                }
            }
        }
    }

    private boolean viewRentalItemDetails(Scanner sc, Item item, long userId) throws ServiceException {
        Optional<User> owner = userService.findUser(item.getOwnerId());
        String listed = item.getCreatedAt().atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.systemDefault()).format(formatter);

        System.out.println("\n=== " + item.getItemName() + " ===");
        System.out.println("description     " + item.getDescription());
        System.out.println("cost per day    " + item.getCostPerDay());
        System.out.println("status          " + item.getStatus());
        System.out.println("owner           " + owner.get().getUsername());
        System.out.println("listed          " + listed);
        System.out.println("1) Rent");
        System.out.println("2) Back to list");
        while (true) {
            int input = validateInputInt(sc);
            switch (input) {
                case 1 -> {
                    System.out.println("Enter the rental duration in days");
                    int days = validateInputInt(sc);
                    try {
                        rentalService.recordRental(item.getId(), userId, days);
                        System.out.println("Item rented successfully.");
                    } catch (ServiceException e) {
                        System.err.println(e.getMessage());
                    }
                    return true;
                }
                case 2 -> {
                    return false;
                }
                default -> System.err.println("Invalid choice, pick 1 or 2.");
            }
        }
    }

    private void returnItem(Scanner sc, long userId) throws ServiceException {
        List<Rental> rentedItemsList = new ArrayList<>();
        boolean loop = true;
        int pageNr = 1;
        int input = 0;
        while(loop) {
            rentedItemsList = rentalService.findActiveRentals(userId ,pageNr);
            System.out.println("\n=== Rented items ===");
            if (rentedItemsList.size() == 0) {
                System.out.println("No items to return.");
                return;
            }
            int lineNr = 1;
            for (Rental entry : rentedItemsList) {
                if (lineNr == 6) {
                    break;
                }
                Optional<Item> item = itemService.findItemByID(entry.getItemId());
                Optional<User> renter = userService.findUser(entry.getRenterId());
                String endTime = entry.getEndTime().atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.systemDefault()).format(formatter);
                System.out.printf("%d) %-8s %-6s %s%n", lineNr, item.get().getItemName(), renter.get().getUsername(), endTime);
                lineNr++;
            }
            input = pagePagination(sc, rentedItemsList.size(), pageNr, lineNr);
            if (input == NEXT_PAGE){
                pageNr += 1;
            } else if (input == PREV_PAGE) {
                pageNr -= 1;
            } else if (input == BACK_TO_MENU) {
                loop = false;
            } else {
                Rental rentalItem = rentedItemsList.get(input -1);
                Optional<Item> item = itemService.findItemByID(rentalItem.getItemId());
                Optional<User> renter = userService.findUser(rentalItem.getRenterId());
                String startTime = rentalItem.getStartTime().atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.systemDefault()).format(formatter);
                String endTime = rentalItem.getEndTime().atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.systemDefault()).format(formatter);
                System.out.println("\n=== " + item.get().getItemName() + " ===");
                System.out.println("renter  " + renter.get().getUsername());
                System.out.println("start   " + startTime);
                System.out.println("end     " + endTime);
                System.out.println("status  " + rentalItem.getStatus());
                System.out.println("1) Confirm return");
                System.out.println("2) Back to list");
                int returnInput = validateInputInt(sc);

                switch (returnInput) {
                    case 1:
                        try {
                            rentalService.confirmReturn(rentalItem.getRentalId(), userId);    
                            System.out.print("Item returned successfully.\n");
                        } catch (ServiceException e) {
                            System.err.println(e.getMessage());
                        }
                        return;
                    case 2:
                        break;
                    default:
                        System.out.println("Invalid choice.");
                        break;
                }
            }
        }       
    }
}
