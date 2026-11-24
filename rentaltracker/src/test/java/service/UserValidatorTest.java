package service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.example.service.UserValidator;
import com.example.service.exception.EmptyPasswordException;
import com.example.service.exception.EmptyUsernameException;

public class UserValidatorTest {

    @Test 
    void acceptsValidUsernameAndPassword() throws Exception{
        assertEquals("name", UserValidator.validateUsername(" name "));
        assertDoesNotThrow(() -> UserValidator.validatePassword(" password "));
    }

    @ParameterizedTest 
    @NullAndEmptySource 
    @ValueSource (strings = {" ", "\t"})
    void rejectsMissingOrBlankUsername(String username){
        assertThrowsExactly(EmptyUsernameException.class, () -> UserValidator.validateUsername(username));
    }

    @ParameterizedTest 
    @NullAndEmptySource 
    @ValueSource (strings = {" ", "\t"})
    void rejectsMissingOrBlankPassword(String password){
        assertThrowsExactly(EmptyPasswordException.class, () -> UserValidator.validatePassword(password));
    }

}
