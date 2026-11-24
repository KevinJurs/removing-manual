package com.example.service;

import java.util.List;
import java.util.Optional;

import com.example.domain.Item;
import com.example.infrastructure.AppLogger;
import com.example.repository.ItemRepository;
import com.example.repository.RentalRepository;
import com.example.repository.exception.RepositoryException;
import com.example.service.exception.ServiceException;

public class ItemService {
    private final ItemRepository itemRepository;
    private final RentalRepository rentalRepository;
    private final AppLogger logger;

    public ItemService(ItemRepository itemRepository, RentalRepository rentalRepository, AppLogger logger){
        this.itemRepository = itemRepository;
        this.rentalRepository = rentalRepository;
        this.logger = logger;
    }

    public Item createItem(long ownerId, String itemName, String description, int costPerDay) throws ServiceException {

        //validate item details and log validation errors
        try {
        itemName = ItemValidator.validateItemname(itemName);
        ItemValidator.validateListingDetails(description, costPerDay);

        } catch (ServiceException e) {
            logger.log("WARN", "createItem.failed", "reason=invalid_input owner_id=" + ownerId);
            throw e;
        }

        try {
            Item item = itemRepository.createItem(ownerId, itemName, description, costPerDay);
            logger.log("INFO", "createItem.success", "item_id=" + item.getId() + " owner_id=" + ownerId);
            return item;

        } catch (RepositoryException e){
            throw new ServiceException("Could not list item", e);
        }
    }

    public List<Item> itemListWithOffset(long userID, int pageNr) throws ServiceException{
        int offset = 0;
        if (pageNr > 1) {
            offset = (pageNr - 1) * 5;
        }
        try {
            return itemRepository.itemList(userID, offset);
        } catch (RepositoryException e){
            throw new ServiceException("Could not get the item list", e);
        }
    }

    public Optional<Item> findItemByID(long itemID) throws ServiceException {
        try {
            return itemRepository.findById(itemID);
        } catch (RepositoryException e){
            throw new ServiceException("Could not find the item: " + itemID, e);
        }
    }

    public List<Item> getAvailableItems(long userID, int pageNr) throws ServiceException {
        int offset = 0;
        if (pageNr > 1) {
            offset = (pageNr - 1) * 5;
        }
        try {
            return itemRepository.itemListAvailable(userID, offset);
        } catch (RepositoryException e){
            throw new ServiceException("Could not get the item list", e);
        }
    }

    public void changeItemStatus(long userId, String statusChange, long itemID) throws ServiceException {
        try {
            // Getting current (string) state of the item
            Item item = itemRepository.getById(itemID);
            if (item.getOwnerId() != userId){
                logger.log("WARN", "changeItemStatus.failed", "reason=foreign_item user_id=" + userId + " item_id=" + itemID);
                throw new ServiceException("You can only change the status of your own items.", null);
            }
            // Converting the string into an ENUM
            ItemListingState state = ItemListingState.valueOf(item.getStatus());
            // Check to prevent relisting an item with an active rental
            if (statusChange.equals("relist") && rentalRepository.hasActiveRental(itemID)){
                logger.log("WARN", "changeItemStatus.failed", "reason=active_rental user_id=" + userId + " item_id=" + itemID);
                throw new ServiceException("Cannot relist an item with an active rental", null);
            }
            // Checking if the requested state change can be done on the current state
            state = validStateChange(statusChange, state);
        
            // SQL update to change the state
            itemRepository.changeItemStatus(userId, state, itemID);
            //log successful status change
            logger.log("INFO", "changeItemStatus.success", "user_id=" + userId + " item_id=" + itemID);
        } catch (RepositoryException e) {
            throw new ServiceException("Could not change the state of the item", e);
        }
    }

    public static ItemListingState validStateChange(String status, ItemListingState state) {
        switch (status) {
            case "rent" -> state = state.rent();
            case "delist" -> state = state.delist();
            case "relist" -> state = state.relist();
            case "return" -> state = state.returnItem();
            default ->
                throw new IllegalStateException("Invalid state change.");
        }
        return state;
    }

    // State machine for items
    public enum ItemListingState {

        available {
            @Override
            public ItemListingState rent() {
                return rented;
            }
            @Override
            public ItemListingState delist() {
                return unlisted;
            }
            @Override
            public ItemListingState relist() {
                throw new IllegalStateException("Unavailable state change.");
            }
            @Override 
            public ItemListingState returnItem(){
                throw new IllegalStateException("Item is not rented.");
            }
        },
        rented {
            @Override
            public ItemListingState delist() {
                return unlisted;
            }
            @Override
            public ItemListingState relist() {
                //return available;
                throw new IllegalStateException("Cannot relist a rented item.");
            }
            @Override
            public ItemListingState rent() {
                throw new IllegalStateException("Unavailable state change.");
            }
            @Override
            public ItemListingState returnItem(){
                return available;
            }
        },
        unlisted {
            @Override
            public ItemListingState relist() {
                return available;
            }
            @Override
            public ItemListingState rent() {
                throw new IllegalStateException("Unavailable state change.");
            }
            @Override
            public ItemListingState delist() {
                throw new IllegalStateException("Unavailable state change.");
            }
            @Override 
            public ItemListingState returnItem(){
                return unlisted;
            }
        };

        public abstract ItemListingState rent();
        public abstract ItemListingState delist();
        public abstract ItemListingState relist();
        public abstract ItemListingState returnItem();  
    }

    public boolean hasNextPage(int listSize) {
        if (listSize == 6) {
            return true;
        }
        return false;
    }

    public boolean hasPrevPage(int pageNr) {
        if (pageNr != 1) {
            return true;
        }
        return false;
    }
}
