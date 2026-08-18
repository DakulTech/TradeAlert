package com.tradealert.verificationservice.controller;

import com.tradealert.verificationservice.events.VerificationCompletedEvent;
import com.tradealert.verificationservice.route.VerificationRoutes;
import com.tradealert.verificationservice.observability.VerificationMetrics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping(VerificationRoutes.BASE)
public class VerificationController {

    private final KafkaTemplate<String, VerificationCompletedEvent> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final VerificationMetrics metrics;

    public VerificationController(KafkaTemplate<String, VerificationCompletedEvent> kafkaTemplate,
            RedisTemplate<String, String> redisTemplate,
            VerificationMetrics metrics) {
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
        this.metrics = metrics;
    }

    @GetMapping("/confirm")
    public ResponseEntity<String> confirmVerification(@RequestParam Long userId,
            @RequestParam String token) {
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
            return ResponseEntity.badRequest().body("Invalid or expired verification token");
        }
    }
}
