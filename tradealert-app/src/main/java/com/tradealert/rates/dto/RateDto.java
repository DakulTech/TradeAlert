package com.tradealert.rates.dto;

import com.tradealert.rates.model.Rate;

import java.math.BigDecimal;
import java.time.Instant;

public record RateDto(String currencyPair, BigDecimal rate, Instant timestamp) {

    public static RateDto fromEntity(Rate rate) {
        return new RateDto(rate.getCurrencyPair(), rate.getRate(), rate.getTimestamp());
    }
}
