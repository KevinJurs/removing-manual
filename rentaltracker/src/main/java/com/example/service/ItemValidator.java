package com.example.service;

import com.example.service.exception.EmptyItemnameException;
import com.example.service.exception.ServiceException;

public final class ItemValidator {

    private ItemValidator(){}

        public static String validateItemname(String itemname) throws ServiceException{
        if (itemname == null || itemname.isBlank()){
            throw new EmptyItemnameException("Itemname cannot be empty.");
        }           
        return itemname.strip();

        }

        public static void validateListingDetails(String description, int costPerDay) throws ServiceException{
            if (description == null || description.isBlank()){
                throw new ServiceException("Item description cannot be empty.", null);
        }
            if (costPerDay < 0){

                throw new ServiceException("Cost per day cannot be negative.", null);
        }    
    }
}
