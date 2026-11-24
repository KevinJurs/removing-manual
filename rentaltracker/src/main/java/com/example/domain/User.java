package com.example.domain;

import java.time.LocalDateTime;

public class User {
    private final long id;
    private final String username;
    private final LocalDateTime createdAt;

    public User(long id, String username, LocalDateTime createdAt){
        this.id = id;
        this.username = username;
        this.createdAt = createdAt;
    }

    public long getId(){
        return id;
    }

    public String getUsername(){
        return username;
    }

    public LocalDateTime getCreatedAt(){
        return createdAt;
    }


}
