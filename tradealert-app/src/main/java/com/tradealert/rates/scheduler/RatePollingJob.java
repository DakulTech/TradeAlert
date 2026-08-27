package com.tradealert.rates.scheduler;

import com.tradealert.rates.service.RateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class RatePollingJob {

    private static final Logger log = LoggerFactory.getLogger(RatePollingJob.class);

    private final RateService rateService;
    private final List<String> currencyPairs;

    public RatePollingJob(RateService rateService,
            @Value("${rates.currency-pairs:}") String configuredPairs) {
        this.rateService = rateService;
        this.currencyPairs = Arrays.stream(configuredPairs.split(","))
                .map(String::trim)
                .filter(pair -> !pair.isBlank())
                .toList();
    }

    @Scheduled(fixedDelayString = "${rates.polling.interval-ms:300000}")
    public void pollExchangeRates() {
        if (currencyPairs.isEmpty()) {
            return;
        }

        for (String currencyPair : currencyPairs) {
            try {
                if (rateService.fetchAndIngestRate(currencyPair) == null) {
                    log.warn("No rate received for {}", currencyPair);
                }
            } catch (Exception exception) {
                log.error("Rate polling failed for {}", currencyPair, exception);
            }
        }
    }
}
