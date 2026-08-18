package com.tradealert.alertservice.events;

import java.math.BigDecimal;
import java.time.Instant;

public class RateEvent {
    private String currencyPair; // e.g. "USD/NGN"
    private BigDecimal rate; // current rate value
    private Instant timestamp; // when the rate was fetched

    public RateEvent() {
    }

    public RateEvent(String currencyPair, BigDecimal rate, Instant timestamp) {
        this.currencyPair = currencyPair;
        this.rate = rate;
        this.timestamp = timestamp;
    }

    public String getCurrencyPair() {
        return currencyPair;
    }

    public void setCurrencyPair(String currencyPair) {
        this.currencyPair = currencyPair;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
