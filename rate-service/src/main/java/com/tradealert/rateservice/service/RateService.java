package com.tradealert.rateservice.service;

import com.tradealert.rateservice.model.Rate;
import com.tradealert.rateservice.repository.RateRepository;
import com.tradealert.rateservice.events.RateEvent;
import com.tradealert.rateservice.producer.RateProducer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
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
    private final Tracer tracer;
    private final Counter providerFailures;
    private final Counter secondaryFallbacks;
    private final Counter ratesIngested;
    private final Timer providerLatency;

    public RateService(RateRepository rateRepository,
            RateProducer rateProducer,
            WebClient.Builder webClientBuilder,
            Tracer tracer,
            io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.rateRepository = rateRepository;
        this.rateProducer = rateProducer;
        this.tracer = tracer;
        this.providerFailures = Counter.builder("rate_provider_failures_total")
                .description("External rate provider calls that failed")
                .tag("service", "rate-service")
                .register(meterRegistry);
        this.secondaryFallbacks = Counter.builder("rate_provider_fallbacks_total")
                .description("Requests served by the secondary rate provider")
                .tag("service", "rate-service")
                .register(meterRegistry);
        this.ratesIngested = Counter.builder("rates_ingested_total")
                .description("Rates persisted and published to Kafka")
                .tag("service", "rate-service")
                .register(meterRegistry);
        this.providerLatency = Timer.builder("rate_provider_latency")
                .description("External rate provider response latency")
                .tag("service", "rate-service")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
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
        Span span = tracer.spanBuilder("rate.fetch_and_ingest").startSpan();
        try (var scope = span.makeCurrent()) {
            span.setAttribute("currency_pair", currencyPair);
            Double primaryRate = fetchRate(primaryClient, currencyPair, "Primary");
            Double secondaryRate = null;

            if (primaryRate == null) {
                secondaryFallbacks.increment();
                span.addEvent("primary_provider_failed_using_secondary");
                secondaryRate = fetchRate(secondaryClient, currencyPair, "Secondary");
            }

            Double chosenRate = chooseBestRate(primaryRate, secondaryRate);

            if (chosenRate != null) {
                span.setAttribute("provider", primaryRate != null ? "primary" : "secondary");
                return ingestRate(currencyPair, chosenRate);
            }

            span.setStatus(StatusCode.ERROR, "Both rate providers failed");
            span.addEvent("rate_provider_exhausted");
            log.error("Both APIs failed for {}", currencyPair);
            return null;
        } finally {
            span.end();
        }
    }

    private Double fetchRate(WebClient client, String currencyPair, String sourceName) {
        Span span = tracer.spanBuilder("rate.provider_call").startSpan();
        Timer.Sample sample = Timer.start();
        try {
            span.setAttribute("provider", sourceName.toLowerCase());
            span.setAttribute("currency_pair", currencyPair);
            return client.get()
                    .uri("/rates/{pair}", currencyPair)
                    .retrieve()
                    .bodyToMono(Double.class)
                    .timeout(java.time.Duration.ofSeconds(5))
                    .onErrorResume(e -> {
                        providerFailures.increment();
                        span.recordException(e);
                        span.setStatus(StatusCode.ERROR, "Provider request failed");
                        log.error("{} API failed for {}: {}", sourceName, currencyPair, e.getMessage());
                        return Mono.empty();
                    })
                    .block();
        } catch (Exception e) {
            providerFailures.increment();
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, "Provider request failed");
            log.error("Unexpected error calling {} API for {}: {}", sourceName, currencyPair, e.getMessage(), e);
            return null;
        } finally {
            sample.stop(providerLatency);
            span.end();
        }
    }

    private Double chooseBestRate(Double primaryRate, Double secondaryRate) {
        if (primaryRate != null && secondaryRate != null) {
            // If both succeed, pick the one closer to each other (simple reliability check)
            double diff = Math.abs(primaryRate - secondaryRate);
            if (diff < 0.0001) {
                Span.current().addEvent("rate_providers_agree");
                return primaryRate;
            } else {
                Span.current().addEvent("rate_provider_values_differ");
                return primaryRate;
            }
        }
        return primaryRate != null ? primaryRate : secondaryRate;
    }

    public Rate ingestRate(String currencyPair, double rateValue) {
        Span span = tracer.spanBuilder("rate.ingest").startSpan();
        try (var scope = span.makeCurrent()) {
            span.setAttribute("currency_pair", currencyPair);
            span.setAttribute("rate_value", rateValue);
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
            ratesIngested.increment();

            return saved;
        } finally {
            span.end();
        }
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
