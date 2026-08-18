package com.tradealert.alertservice.controller;

import com.tradealert.alertservice.model.Alert;
import com.tradealert.alertservice.service.AlertService;
import com.tradealert.alertservice.route.AlertRoutes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(AlertRoutes.BASE)
public class AlertController {

    private static final Logger log = LoggerFactory.getLogger(AlertController.class);

    private final AlertService alertService;
    private final Tracer tracer;
    private final Counter alertsCreatedCounter;
    private final Counter alertsUpdatedCounter;
    private final Counter alertsDeletedCounter;

    public AlertController(AlertService alertService, Tracer tracer, MeterRegistry meterRegistry) {
        this.alertService = alertService;
        this.tracer = tracer;
        this.alertsCreatedCounter = Counter.builder("alerts_created_total")
                .description("Total alerts created")
                .register(meterRegistry);
        this.alertsUpdatedCounter = Counter.builder("alerts_updated_total")
                .description("Total alerts updated")
                .register(meterRegistry);
        this.alertsDeletedCounter = Counter.builder("alerts_deleted_total")
                .description("Total alerts deleted")
                .register(meterRegistry);
    }

    @GetMapping
    public ResponseEntity<List<Alert>> getAllAlerts() {
        Span span = tracer.spanBuilder("getAllAlerts").startSpan();
        try {
            List<Alert> alerts = alertService.getAllAlerts();
            return ResponseEntity.ok(alerts);
        } catch (Exception ex) {
            log.error("Error fetching alerts", ex);
            span.recordException(ex);
            return ResponseEntity.internalServerError().build();
        } finally {
            span.end();
        }
    }

    @PostMapping
    public ResponseEntity<Alert> createAlert(@RequestBody Alert alert) {
        Span span = tracer.spanBuilder("createAlert").startSpan();
        try {
            Alert created = alertService.createAlert(alert);
            alertsCreatedCounter.increment();
            log.info("Created alert {}", created.getId());
            return ResponseEntity.ok(created);
        } catch (Exception ex) {
            log.error("Error creating alert", ex);
            span.recordException(ex);
            return ResponseEntity.internalServerError().build();
        } finally {
            span.end();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Alert> updateAlert(@PathVariable Long id, @RequestBody Alert alert) {
        Span span = tracer.spanBuilder("updateAlert").startSpan();
        try {
            Alert updated = alertService.updateAlert(id, alert);
            alertsUpdatedCounter.increment();
            log.info("Updated alert {}", updated.getId());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException ex) {
            log.warn("Alert not found {}", id, ex);
            return ResponseEntity.notFound().build();
        } catch (Exception ex) {
            log.error("Error updating alert {}", id, ex);
            span.recordException(ex);
            return ResponseEntity.internalServerError().build();
        } finally {
            span.end();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlert(@PathVariable Long id) {
        Span span = tracer.spanBuilder("deleteAlert").startSpan();
        try {
            alertService.deleteAlert(id);
            alertsDeletedCounter.increment();
            log.info("Deleted alert {}", id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            log.warn("Alert not found {}", id, ex);
            return ResponseEntity.notFound().build();
        } catch (Exception ex) {
            log.error("Error deleting alert {}", id, ex);
            span.recordException(ex);
            return ResponseEntity.internalServerError().build();
        } finally {
            span.end();
        }
    }
}
