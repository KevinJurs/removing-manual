package service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.example.service.RentalValidator;
import com.example.service.exception.ServiceException;

public class RentalValidatorTest {
    

    //test for zero duration value
    @Test
    void recordRentalRejectsZeroDuration() {

        assertThrows(ServiceException.class, () -> RentalValidator.validateDurationDays(0));
}
    //test for negative duration value
    @Test
    void recordRentalRejectsNegativeDuration() {

        assertThrows(ServiceException.class, () -> RentalValidator.validateDurationDays(0));
}
}
