package com.tradealert.alertservice.consumer;

import com.tradealert.alertservice.events.RateEvent;
import com.tradealert.alertservice.events.AlertTriggeredEvent;
import com.tradealert.alertservice.model.Alert;
import com.tradealert.alertservice.repository.AlertRepository;
import com.tradealert.alertservice.service.AlertService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class RateConsumer {

    private static final Logger log = LoggerFactory.getLogger(RateConsumer.class);

    private final AlertRepository alertRepository;
    private final AlertService alertService;
    private final KafkaTemplate<String, AlertTriggeredEvent> kafkaTemplate;
    private final Tracer tracer;
    private final Counter alertsTriggeredCounter;
    private final Counter rateEventsConsumedCounter;
    private final Counter alertPublishFailuresCounter;

    public RateConsumer(AlertRepository alertRepository,
            AlertService alertService,
            KafkaTemplate<String, AlertTriggeredEvent> kafkaTemplate,
            Tracer tracer,
            MeterRegistry meterRegistry) {
        this.alertRepository = alertRepository;
        this.alertService = alertService;
        this.kafkaTemplate = kafkaTemplate;
        this.tracer = tracer;
        this.alertsTriggeredCounter = Counter.builder("alerts_triggered_total")
                .description("Total alerts triggered")
                .register(meterRegistry);
        this.rateEventsConsumedCounter = Counter.builder("rate_events_consumed_total")
                .description("Rate events consumed by the alert service")
                .register(meterRegistry);
        this.alertPublishFailuresCounter = Counter.builder("alert_publish_failures_total")
                .description("Alert events that failed to publish")
                .register(meterRegistry);
    }

    @KafkaListener(topics = "rates", groupId = "alert-service")
    public void consumeRate(RateEvent rateEvent) {
        Span span = tracer.spanBuilder("alert.consume_rate").startSpan();
        try (var scope = span.makeCurrent()) {
            rateEventsConsumedCounter.increment();
            span.setAttribute("currency_pair", rateEvent.getCurrencyPair());
            span.setAttribute("rate", rateEvent.getRate().doubleValue());
            log.info("Processing rate event for {}", rateEvent.getCurrencyPair());

            List<Alert> alerts = alertRepository.findActiveAlerts(rateEvent.getCurrencyPair(), rateEvent.getRate());
            for (Alert alert : alerts) {
                Instant triggeredAt = Instant.now();
                if (!alertService.claimForNotification(alert.getId(), triggeredAt)) {
                    span.addEvent("alert_claim_already_taken");
                    continue;
                }

                // Build enriched event
                AlertTriggeredEvent triggeredEvent = new AlertTriggeredEvent();
                triggeredEvent.setAlertId(alert.getId());
                triggeredEvent.setUserId(alert.getUser().getId());
                triggeredEvent.setCurrencyPair(alert.getCurrencyPair().getSymbol());
                triggeredEvent.setTargetRate(alert.getTargetRate());
                triggeredEvent.setTriggeredRate(rateEvent.getRate());
                triggeredEvent.setTriggeredAt(triggeredAt);

                // Publish to Kafka
                try {
                    kafkaTemplate.send("alerts", triggeredEvent);
                } catch (Exception publishException) {
                    alertPublishFailuresCounter.increment();
                    span.recordException(publishException);
                    span.setStatus(StatusCode.ERROR, "Alert event publication failed");
                    throw publishException;
                }
                alertsTriggeredCounter.increment();

                log.info("Triggered alert {} for user {} on {} at rate {}",
                        alert.getId(),
                        alert.getUser().getId(),
                        alert.getCurrencyPair().getSymbol(),
                        rateEvent.getRate());
            }
        } catch (Exception ex) {
            log.error("Error processing rate event {}: {}", rateEvent, ex.getMessage(), ex);
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR, "Rate event processing failed");
        } finally {
            span.end();
        }
    }
}
