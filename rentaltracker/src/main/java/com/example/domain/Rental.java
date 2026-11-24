package com.example.domain;

import java.time.LocalDateTime;

public class Rental {
    
    private long rentalId;
    private long itemId;
    private long renterId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime returnedAt;
    private String status;


public Rental(
    long rentalId,
    long itemId,
    long renterId,
    LocalDateTime startTime,
    LocalDateTime endTime,
    LocalDateTime returnedAt,
    String status) {

    this.rentalId = rentalId;
    this.itemId = itemId;
    this.renterId = renterId;
    this.startTime = startTime;
    this.endTime = endTime;
    this.returnedAt = returnedAt;
    this.status = status;
}

public long getRentalId() {
    return rentalId;
}

public long getItemId() {
    return itemId;
}

public long getRenterId() {
    return renterId;
}

public LocalDateTime getStartTime() {
    return startTime;
}

public LocalDateTime getEndTime() {
    return endTime;
}

public LocalDateTime getReturnedAt() {
    return returnedAt;
}

public String getStatus() {
    return status;
}

}