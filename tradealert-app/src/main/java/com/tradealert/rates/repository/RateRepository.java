package com.tradealert.rates.repository;

import com.tradealert.rates.model.Rate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RateRepository extends JpaRepository<Rate, Long> {

    Optional<Rate> findTopByCurrencyPairOrderByTimestampDesc(String currencyPair);
}
