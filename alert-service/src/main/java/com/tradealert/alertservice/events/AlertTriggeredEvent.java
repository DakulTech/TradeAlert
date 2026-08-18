package com.tradealert.alertservice.events;

import java.math.BigDecimal;
import java.time.Instant;

public class AlertTriggeredEvent {
    private Long alertId; // unique ID of the alert
    private Long userId; // user who owns the alert
    private String currencyPair; // e.g. "USD/NGN"
    private BigDecimal targetRate; // the threshold set by the user
    private BigDecimal triggeredRate;// the actual rate that triggered
    private Instant triggeredAt; // when the alert was triggered

    public AlertTriggeredEvent() {
    }

    public AlertTriggeredEvent(Long alertId, Long userId, String currencyPair, BigDecimal targetRate,
            BigDecimal triggeredRate, Instant triggeredAt) {
        this.alertId = alertId;
        this.userId = userId;
        this.currencyPair = currencyPair;
        this.targetRate = targetRate;
        this.triggeredRate = triggeredRate;
        this.triggeredAt = triggeredAt;
    }

    public Long getAlertId() {
        return alertId;
    }

    public void setAlertId(Long alertId) {
        this.alertId = alertId;
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
