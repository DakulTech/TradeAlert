package com.tradealert.rateservice.producer;

import com.tradealert.rateservice.events.RateEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

@Component
public class RateProducer {

    private final KafkaTemplate<String, RateEvent> kafkaTemplate;
    private final Tracer tracer;
    private final Counter publishFailures;

    public RateProducer(KafkaTemplate<String, RateEvent> kafkaTemplate,
            Tracer tracer,
            MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.tracer = tracer;
        this.publishFailures = Counter.builder("rate_event_publish_failures_total")
                .description("Rate events that failed to publish")
                .register(meterRegistry);
    }

    public void publishRate(RateEvent event) {
        Span span = tracer.spanBuilder("rate.publish_event").startSpan();
        try (var scope = span.makeCurrent()) {
            span.setAttribute("currency_pair", event.getCurrencyPair());
            // keys each message by currency pair to ensure ordering for the same pair
            kafkaTemplate.send("rates", event.getCurrencyPair(), event).whenComplete((result, exception) -> {
                if (exception != null) {
                    publishFailures.increment();
                    span.recordException(exception);
                    span.setStatus(StatusCode.ERROR, "Rate event publication failed");
                }
                span.end();
            });
        } catch (Exception exception) {
            publishFailures.increment();
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, "Rate event publication failed");
            span.end();
            throw new IllegalStateException("Rate event publication failed", exception);
        }
    }
}
