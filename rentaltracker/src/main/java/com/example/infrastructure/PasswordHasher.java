package com.example.infrastructure;

import com.password4j.Password;

public final class PasswordHasher {
    
    public String hash(String password){
        return Password.hash(password).addRandomSalt(16).withArgon2().getResult();
    }

    public boolean matches(String password, String hash){
        return Password.check(password, hash).withArgon2();
    }
}
