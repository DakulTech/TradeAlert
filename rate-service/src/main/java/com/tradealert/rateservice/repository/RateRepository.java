package com.tradealert.rateservice.repository;

import com.tradealert.rateservice.model.Rate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RateRepository extends JpaRepository<Rate, Long> {

    /**
     * Fetch the most recent rate for a given currency pair.
     * Used for fallback when external APIs fail or for reliability comparison.
     */
    Optional<Rate> findTopByCurrencyPairOrderByTimestampDesc(String currencyPair);
}
