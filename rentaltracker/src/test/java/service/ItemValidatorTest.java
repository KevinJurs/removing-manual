package service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.example.service.ItemValidator;
import com.example.service.exception.EmptyItemnameException;

public class ItemValidatorTest {
    
    @Test 
    void nullItemnameThrowsException() throws Exception {
        assertThrows(EmptyItemnameException.class, () -> ItemValidator.validateItemname(null));
    }

    @Test 
    void emptyItemnameThrowsException() throws Exception {
        assertThrows(EmptyItemnameException.class, () -> ItemValidator.validateItemname(""));
    }

}
