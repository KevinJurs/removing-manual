package com.example.domain;

import java.time.LocalDateTime;

public class Item {

    private final long id;
    private final long ownerId;
    private final String itemName;
    private final String description;
    private final int costPerDay;
    private final String status;
    private final LocalDateTime createdAt;

    public Item(long id, 
                long ownerId, 
                String itemName, 
                String description, 
                int costPerDay, 
                String status, 
                LocalDateTime createdAt) {

        this.id = id;
        this.ownerId = ownerId;
        this.itemName = itemName;
        this.description = description;
        this.costPerDay = costPerDay;
        this.status = status;
        this.createdAt = createdAt;

    }

    public long getId() {

        return id;
    }

    public long getOwnerId() {

        return ownerId;

    }

    public String getItemName() {

        return itemName;
    }
    public String getDescription() {

        return description;
    }

    public int getCostPerDay() {

        return costPerDay;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {

        return createdAt;
    }
}
