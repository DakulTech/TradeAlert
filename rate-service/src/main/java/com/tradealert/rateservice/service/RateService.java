package com.tradealert.rateservice.service;

import com.tradealert.rateservice.model.Rate;
import com.tradealert.rateservice.repository.RateRepository;
import com.tradealert.rateservice.events.RateEvent;
import com.tradealert.rateservice.producer.RateProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class RateService {

    private static final Logger log = LoggerFactory.getLogger(RateService.class);

    private final RateRepository rateRepository;
    private final RateProducer rateProducer;
    private final WebClient primaryClient;
    private final WebClient secondaryClient;

    public RateService(RateRepository rateRepository,
            RateProducer rateProducer,
            WebClient.Builder webClientBuilder) {
        this.rateRepository = rateRepository;
        this.rateProducer = rateProducer;
        this.primaryClient = webClientBuilder
                .baseUrl(System.getenv().getOrDefault("PRIMARY_RATE_API_URL", "https://api1.example.com"))
                .build();
        this.secondaryClient = webClientBuilder
                .baseUrl(System.getenv().getOrDefault("SECONDARY_RATE_API_URL", "https://api2.example.com"))
                .build();
    }

    /**
     * Fetch rate from external APIs with failover.
     * If both fail, return null (controller will respond with 503).
     */
    public Rate fetchAndIngestRate(String currencyPair) {
        Double primaryRate = fetchRate(primaryClient, currencyPair, "Primary");
        Double secondaryRate = null;

        if (primaryRate == null) {
            secondaryRate = fetchRate(secondaryClient, currencyPair, "Secondary");
        }

        Double chosenRate = chooseBestRate(primaryRate, secondaryRate);

        if (chosenRate != null) {
            return ingestRate(currencyPair, chosenRate);
        } else {
            log.error("Both APIs failed for {}", currencyPair);
            return null;
        }
    }

    private Double fetchRate(WebClient client, String currencyPair, String sourceName) {
        try {
            return client.get()
                    .uri("/rates/{pair}", currencyPair)
                    .retrieve()
                    .bodyToMono(Double.class)
                    .timeout(java.time.Duration.ofSeconds(5))
                    .onErrorResume(e -> {
                        log.error("{} API failed for {}: {}", sourceName, currencyPair, e.getMessage());
                        return Mono.empty();
                    })
                    .block();
        } catch (Exception e) {
            log.error("Unexpected error calling {} API for {}: {}", sourceName, currencyPair, e.getMessage(), e);
            return null;
        }
    }

    private Double chooseBestRate(Double primaryRate, Double secondaryRate) {
        if (primaryRate != null && secondaryRate != null) {
            // If both succeed, pick the one closer to each other (simple reliability check)
            double diff = Math.abs(primaryRate - secondaryRate);
            if (diff < 0.0001) {
                log.info("Both APIs agree, using Primary");
                return primaryRate;
            } else {
                log.warn("APIs differ significantly, defaulting to Primary");
                return primaryRate;
            }
        }
        return primaryRate != null ? primaryRate : secondaryRate;
    }

    public Rate ingestRate(String currencyPair, double rateValue) {
        Rate rate = new Rate();
        rate.setCurrencyPair(currencyPair);
        rate.setRate(BigDecimal.valueOf(rateValue));
        rate.setTimestamp(Instant.now());

        Rate saved = rateRepository.save(rate);

        RateEvent event = new RateEvent();
        event.setCurrencyPair(currencyPair);
        event.setRate(saved.getRate());
        event.setTimestamp(saved.getTimestamp());

        rateProducer.publishRate(event);

        return saved;
    }

    public List<Rate> getAllRates() {
        return rateRepository.findAll();
    }

    /**
     * Health check for external APIs.
     */
    public Map<String, Boolean> checkApiHealth() {
        boolean primaryHealthy = testApi(primaryClient, "Primary");
        boolean secondaryHealthy = testApi(secondaryClient, "Secondary");

        return Map.of(
                "primaryApi", primaryHealthy,
                "secondaryApi", secondaryHealthy);
    }

    private boolean testApi(WebClient client, String sourceName) {
        try {
            // assumes external API has a /ping or lightweight endpoint
            String result = client.get()
                    .uri("/ping")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(3))
                    .onErrorResume(e -> Mono.empty())
                    .block();
            return result != null;
        } catch (Exception e) {
            log.error("{} API health check failed: {}", sourceName, e.getMessage());
            return false;
        }
    }
}
