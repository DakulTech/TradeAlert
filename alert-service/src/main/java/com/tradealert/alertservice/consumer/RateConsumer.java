package com.tradealert.alertservice.consumer;

import com.tradealert.alertservice.events.RateEvent;
import com.tradealert.alertservice.events.AlertTriggeredEvent;
import com.tradealert.alertservice.model.Alert;
import com.tradealert.alertservice.repository.AlertRepository;
import com.tradealert.alertservice.service.AlertService;
import io.opentelemetry.api.trace.Span;
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
    }

    @KafkaListener(topics = "rates", groupId = "alert-service")
    public void consumeRate(RateEvent rateEvent) {
        Span span = tracer.spanBuilder("consumeRate").startSpan();
        try {
            log.info("Processing rate event for {}", rateEvent.getCurrencyPair());

            List<Alert> alerts = alertRepository.findActiveAlerts(rateEvent.getCurrencyPair(), rateEvent.getRate());
            for (Alert alert : alerts) {
                // Mark as notified inside a transaction
                alertService.markAsNotified(alert);

                // Build enriched event
                AlertTriggeredEvent triggeredEvent = new AlertTriggeredEvent();
                triggeredEvent.setAlertId(alert.getId());
                triggeredEvent.setUserId(alert.getUser().getId());
                triggeredEvent.setCurrencyPair(alert.getCurrencyPair().getSymbol());
                triggeredEvent.setTargetRate(alert.getTargetRate());
                triggeredEvent.setTriggeredRate(rateEvent.getRate());
                triggeredEvent.setTriggeredAt(Instant.now());

                // Publish to Kafka
                kafkaTemplate.send("alerts", triggeredEvent);
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
        } finally {
            span.end();
        }
    }
}
