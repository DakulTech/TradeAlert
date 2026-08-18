package com.tradealert.rateservice.events;

import java.math.BigDecimal;
import java.time.Instant;

public class RateEvent {
    private String currencyPair;
    private BigDecimal rate;
    private Instant timestamp;

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
