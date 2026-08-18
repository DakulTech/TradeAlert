package com.tradealert.notificationservice.events;

import java.math.BigDecimal;
import java.time.Instant;

public class AlertTriggeredEvent {
    private Long userId;
    private String currencyPair;
    private BigDecimal targetRate;
    private BigDecimal triggeredRate;
    private Instant triggeredAt;

    public AlertTriggeredEvent() {
    }

    public AlertTriggeredEvent(Long userId, String currencyPair, BigDecimal targetRate,
            BigDecimal triggeredRate, Instant triggeredAt) {
        this.userId = userId;
        this.currencyPair = currencyPair;
        this.targetRate = targetRate;
        this.triggeredRate = triggeredRate;
        this.triggeredAt = triggeredAt;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getCurrencyPair() {
        return currencyPair;
    }

    public void setCurrencyPair(String currencyPair) {
        this.currencyPair = currencyPair;
    }

    public BigDecimal getTargetRate() {
        return targetRate;
    }

    public void setTargetRate(BigDecimal targetRate) {
        this.targetRate = targetRate;
    }

    public BigDecimal getTriggeredRate() {
        return triggeredRate;
    }

    public void setTriggeredRate(BigDecimal triggeredRate) {
        this.triggeredRate = triggeredRate;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(Instant triggeredAt) {
        this.triggeredAt = triggeredAt;
    }
}
