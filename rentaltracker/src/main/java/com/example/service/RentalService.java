package com.example.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.example.domain.Item;
import com.example.domain.Rental;
import com.example.repository.RentalRepository;
import com.example.repository.exception.RepositoryException;
import com.example.service.exception.ServiceException;
import com.example.infrastructure.AppLogger;

public class RentalService {

    private final RentalRepository rentalRepository;
    private final ItemService itemService;
    private final AppLogger logger;


    public RentalService(RentalRepository rentalRepository, ItemService itemService, UserService userService, AppLogger logger) {
        this.rentalRepository = rentalRepository;
        this.itemService = itemService;
        this.logger = logger;
    }

    public Rental recordRental(long itemId, long renterId, int durationDays) throws ServiceException {

        //validates rental duration and log validation errors
        try {
            RentalValidator.validateDurationDays(durationDays);

        } catch (ServiceException e) {
            logger.log("WARN", "validation.failed", "entity=Rental field=durationDays reason=non_positive");
            throw e;
        }

        //find item my its ID
       Item item = itemService.findItemByID(itemId).orElseThrow(() -> new ServiceException("Item not found", null));
       if (item.getOwnerId() == renterId){
            logger.log("WARN", "validation.failed", "entity=Rental field=renterId reason=own_item");
            throw new ServiceException("Cannot rent your own item.", null);
       }

       //check if item is not available
        if(!item.getStatus().equals("available")) {
            logger.log("WARN", "validation.failed", "entity=Rental field=itemStatus reason=not_available_for_rent");
            throw new ServiceException ("Item is not available for rent", null);
        }

        try {
            //checks if the item already has active rental
            if (rentalRepository.hasActiveRental(itemId)) {
                logger.log("WARN", "validation.failed", "entity=Rental field=itemId reason=active_rental_exists");
                throw new ServiceException ("Item already has an active rental.", null);
            }
            LocalDateTime startTime = LocalDateTime.now(ZoneOffset.UTC);
            LocalDateTime endTime = startTime.plusDays(durationDays);

            //create new rental, save it
            Rental rental = rentalRepository.createRental(itemId, renterId, endTime);
            //change item status for rented
            itemService.changeItemStatus(item.getOwnerId(), "rent", itemId);

            logger.log("INFO", "rental.created", "rental_id=" + rental.getRentalId() + " item_id=" + itemId + " renter_id=" + renterId);
            return rental;
       
        } catch (RepositoryException e) {
            throw new ServiceException("Could not create rental.", e);
        }
    }

    public boolean hasActiveRental(long itemID) throws ServiceException {
         try{
            return rentalRepository.hasActiveRental(itemID);
        } catch (RepositoryException e){
            throw new ServiceException("Could not retrieve rental with itemID: " + itemID, e);
        }
    }

    public List<Rental> findActiveRentals(long owner_id, int pageNr) throws ServiceException{
        int offset = 0;
        if (pageNr > 1) {
            offset = (pageNr - 1) * 5;
        }
        try{
            return rentalRepository.findActiveRentals(owner_id, offset);
        } catch (RepositoryException e){
            throw new ServiceException("Could not retrieve active rentals", e);
        }
    }

    public Optional<Rental> findRentalById(long rentalId) throws ServiceException{
        try{
            return rentalRepository.findById(rentalId);
        } catch (RepositoryException e){
            throw new ServiceException("Could not find rental", e);
        }
    }

    public void confirmReturn(long rentalId, long userId) throws ServiceException{
        try {
            Rental rental = rentalRepository.findById(rentalId).orElseThrow(() -> { 
                logger.log("WARN", "validation.failed", "entity=Rental field=rentalId reason=rental_not_found");
                return new ServiceException("Rental not found.", null);
            });

            if (!rental.getStatus().equals("active")){
                logger.log("WARN", "validation.failed", "entity=Rental field=status reason=rental_already_closed");
                throw new ServiceException("Rental is already closed", null);
            }

            Item item = itemService.findItemByID(rental.getItemId()).orElseThrow(() -> {
                logger.log("WARN", "validation.failed", "entity=Rental field=itemId reason=item_not_found");
                return new ServiceException("Item not found.", null);
            });

            if (!item.getStatus().equals("rented") && !item.getStatus().equals("unlisted")){
                logger.log("WARN", "validation.failed", "entity=Rental field=itemStatus reason=invalid_return_state");
                throw new ServiceException("Item is not in a valid state for return", null);
            }

            if (item.getOwnerId() != userId){
                logger.log("WARN", "validation.failed", "entity=Rental field=userId reason=not_item_owner");
                throw new ServiceException("You can only confirm the return of your own items", null);
            }
            rentalRepository.confirmReturn(rentalId);

            if(item.getStatus().equals("rented")){
                itemService.changeItemStatus(item.getOwnerId(), "return", item.getId());
            }
            logger.log("INFO", "return.confirmed", "rental_id=" + rental.getRentalId() + " item_id=" + rental.getItemId());

        } catch (RepositoryException e){
            throw new ServiceException("Could not confirm rental return", e);
        }
    }
}
