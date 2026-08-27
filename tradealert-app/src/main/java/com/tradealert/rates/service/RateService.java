package com.tradealert.rates.service;

import com.tradealert.alerts.model.Alert;
import com.tradealert.alerts.service.AlertService;
import com.tradealert.notifications.model.AlertNotification;
import com.tradealert.notifications.service.NotificationService;
import com.tradealert.rates.model.Rate;
import com.tradealert.rates.repository.RateRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class RateService {

    private static final Logger log = LoggerFactory.getLogger(RateService.class);

    private final RateRepository rateRepository;
    private final AlertService alertService;
    private final NotificationService notificationService;
    private final WebClient primaryClient;
    private final WebClient secondaryClient;
    private final Counter providerFailures;
    private final Counter secondaryFallbacks;
    private final Counter ratesIngested;
    private final Timer providerLatency;

    public RateService(RateRepository rateRepository, AlertService alertService,
            NotificationService notificationService,
            WebClient.Builder webClientBuilder,
            MeterRegistry meterRegistry,
            @Value("${rateapis.primary-url}") String primaryUrl,
            @Value("${rateapis.secondary-url}") String secondaryUrl) {
        this.rateRepository = rateRepository;
        this.alertService = alertService;
        this.notificationService = notificationService;
        this.primaryClient = webClientBuilder.baseUrl(primaryUrl).build();
        this.secondaryClient = webClientBuilder.baseUrl(secondaryUrl).build();
        this.providerFailures = Counter.builder("rate_provider_failures_total").register(meterRegistry);
        this.secondaryFallbacks = Counter.builder("rate_provider_fallbacks_total").register(meterRegistry);
        this.ratesIngested = Counter.builder("rates_ingested_total").register(meterRegistry);
        this.providerLatency = Timer.builder("rate_provider_latency")
                .publishPercentiles(0.5, 0.95, 0.99).register(meterRegistry);
    }

    public Rate fetchAndIngestRate(String currencyPair) {
        Double primaryRate = fetchRate(primaryClient, currencyPair, "Primary");
        Double secondaryRate = null;
        if (primaryRate == null) {
            secondaryFallbacks.increment();
            secondaryRate = fetchRate(secondaryClient, currencyPair, "Secondary");
        }
        Double chosenRate = primaryRate != null ? primaryRate : secondaryRate;
        return chosenRate == null ? null : ingestRate(currencyPair, chosenRate);
    }

    private Double fetchRate(WebClient client, String currencyPair, String sourceName) {
        Timer.Sample sample = Timer.start();
        try {
            return client.get().uri("/rates/{pair}", currencyPair)
                    .retrieve().bodyToMono(Double.class)
                    .timeout(Duration.ofSeconds(5))
                    .onErrorResume(exception -> {
                        providerFailures.increment();
                        log.error("{} API failed for {}: {}", sourceName, currencyPair, exception.getMessage());
                        return Mono.empty();
                    }).block();
        } catch (Exception exception) {
            providerFailures.increment();
            log.error("Unexpected error calling {} API for {}", sourceName, currencyPair,
                    exception.getMessage(), exception);
            return null;
        } finally {
            sample.stop(providerLatency);
        }
    }

    public Rate ingestRate(String currencyPair, double rateValue) {
        Rate rate = new Rate(currencyPair, BigDecimal.valueOf(rateValue), Instant.now());
        Rate saved = rateRepository.save(rate);
        List<Alert> matchingAlerts = alertService.findMatchingAlerts(currencyPair, saved.getRate());
        for (Alert alert : matchingAlerts) {
            Instant triggeredAt = Instant.now();
            if (alertService.claimForNotification(alert.getId(), triggeredAt)) {
                notificationService.sendOrQueue(new AlertNotification(
                        alert.getId(), alert.getUser().getId(), alert.getCurrencyPair().getSymbol(),
                        alert.getTargetRate(), saved.getRate(), triggeredAt));
            }
        }
        ratesIngested.increment();
        return saved;
    }

    public List<Rate> getAllRates() {
        return rateRepository.findAll();
    }

    public Map<String, Boolean> checkApiHealth() {
        return Map.of("primaryApi", testApi(primaryClient), "secondaryApi", testApi(secondaryClient));
    }

    private boolean testApi(WebClient client) {
        try {
            return client.get().uri("/ping").retrieve().bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(3)).onErrorResume(exception -> Mono.empty()).block() != null;
        } catch (Exception exception) {
            return false;
        }
    }
}
