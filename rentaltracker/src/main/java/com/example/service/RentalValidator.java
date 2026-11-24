package com.example.service;

import com.example.service.exception.ServiceException;

public final class RentalValidator {
     private RentalValidator() {}

     public static void validateDurationDays(int durationDays) throws ServiceException {

        if (durationDays <= 0) {
            
            throw new ServiceException("Rental duration must be greater than zero.", null);
       
        }
     }
}
