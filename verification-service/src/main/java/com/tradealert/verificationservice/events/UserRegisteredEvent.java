package com.tradealert.verificationservice.events;

import java.time.Instant;

public class UserRegisteredEvent {
    private Long userId;
    private String email;
    private Instant timestamp;

    public UserRegisteredEvent() {
    }

    public UserRegisteredEvent(Long userId, String email, Instant timestamp) {
        this.userId = userId;
        this.email = email;
        this.timestamp = timestamp;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
