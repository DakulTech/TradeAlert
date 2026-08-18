package com.tradealert.rateservice.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class RateDTO {

    private String currencyPair;
    private BigDecimal rate;
    private Instant timestamp;

    public RateDTO() {
    }

    public RateDTO(String currencyPair, BigDecimal rate, Instant timestamp) {
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

    @Override
    public String toString() {
        return "RateDTO{" +
                "currencyPair='" + currencyPair + '\'' +
                ", rate=" + rate +
                ", timestamp=" + timestamp +
                '}';
    }

    public static RateDTO fromEntity(com.tradealert.rateservice.model.Rate rate) {
        return new RateDTO(rate.getCurrencyPair(), rate.getRate(), rate.getTimestamp());
    }
}
