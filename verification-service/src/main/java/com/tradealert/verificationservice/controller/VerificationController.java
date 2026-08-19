package com.tradealert.verificationservice.controller;

import com.tradealert.verificationservice.events.VerificationCompletedEvent;
import com.tradealert.verificationservice.route.VerificationRoutes;
import com.tradealert.verificationservice.observability.VerificationMetrics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

@RestController
@RequestMapping(VerificationRoutes.BASE)
public class VerificationController {

    private final KafkaTemplate<String, VerificationCompletedEvent> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final VerificationMetrics metrics;
    private final Tracer tracer;

    public VerificationController(KafkaTemplate<String, VerificationCompletedEvent> kafkaTemplate,
            RedisTemplate<String, String> redisTemplate,
            VerificationMetrics metrics,
            Tracer tracer) {
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
        this.metrics = metrics;
        this.tracer = tracer;
    }

    @GetMapping("/confirm")
    public ResponseEntity<String> confirmVerification(@RequestParam Long userId,
            @RequestParam String token) {
        Span span = tracer.spanBuilder("verification.confirm_request").startSpan();
        try (var scope = span.makeCurrent()) {
            span.setAttribute("user.id", userId);
            String key = "verify:token:" + token;
            String storedUserId = redisTemplate.opsForValue().get(key);

            VerificationCompletedEvent event = new VerificationCompletedEvent();
            event.setUserId(userId);
            event.setTimestamp(Instant.now());

            if (storedUserId != null && storedUserId.equals(userId.toString())) {
                event.setSuccess(true);
                kafkaTemplate.send("verification-completed", event);

                redisTemplate.delete(key);
                metrics.incrementConfirmed();

                return ResponseEntity.ok("Verification successful for user " + userId);
            } else {
                event.setSuccess(false);
                kafkaTemplate.send("verification-completed", event);

                metrics.incrementFailed();
                span.setStatus(StatusCode.ERROR, "Invalid or expired verification token");
                return ResponseEntity.badRequest().body("Invalid or expired verification token");
            }
        } catch (Exception exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, "Verification confirmation request failed");
            throw new IllegalStateException("Verification confirmation request failed", exception);
        } finally {
            span.end();
        }
    }
}
