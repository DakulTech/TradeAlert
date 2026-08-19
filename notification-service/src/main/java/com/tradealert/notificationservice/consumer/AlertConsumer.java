package com.tradealert.notificationservice.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradealert.notificationservice.events.AlertTriggeredEvent;
import com.tradealert.notificationservice.service.PresenceService;
import com.tradealert.notificationservice.observability.NotificationMetrics;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.RedisTemplate;

@Component
public class AlertConsumer {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final PresenceService presenceService;
    private final NotificationMetrics metrics;
    private final Tracer tracer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AlertConsumer(SimpMessagingTemplate messagingTemplate,
            RedisTemplate<String, String> redisTemplate,
            PresenceService presenceService,
            NotificationMetrics metrics,
            Tracer tracer) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
        this.presenceService = presenceService;
        this.metrics = metrics;
        this.tracer = tracer;
    }

    @KafkaListener(topics = "alerts", groupId = "notification-service")
    public void consumeAlert(AlertTriggeredEvent event) {
        Span span = tracer.spanBuilder("notification.consume_alert").startSpan();
        try (var scope = span.makeCurrent()) {
            span.setAttribute("user.id", event.getUserId());
            span.setAttribute("alert.id", event.getAlertId());
            metrics.incrementConsumed();
            String channel = "/topic/alerts/" + event.getUserId();
            boolean userOnline = presenceService.isUserOnline(event.getUserId());
            span.setAttribute("user.online", userOnline);

            if (userOnline) {
                messagingTemplate.convertAndSend(channel, event);
            } else {
                try {
                    String json = objectMapper.writeValueAsString(event);
                    redisTemplate.opsForList().rightPush("pending:" + event.getUserId(), json);
                } catch (JsonProcessingException e) {
                    // fallback to toString
                    redisTemplate.opsForList().rightPush("pending:" + event.getUserId(), event.toString());
                }
            }
        } catch (Exception exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, "Notification event processing failed");
            throw new IllegalStateException("Notification event processing failed", exception);
        } finally {
            span.end();
        }
    }
}
