package com.tradealert.verificationservice.events;

import java.time.Instant;

public class VerificationCompletedEvent {
    private Long userId;
    private boolean success;
    private Instant timestamp;

    public VerificationCompletedEvent() {
    }

    public VerificationCompletedEvent(Long userId, boolean success, Instant timestamp) {
        this.userId = userId;
        this.success = success;
        this.timestamp = timestamp;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
