package com.tradealert.notificationservice.service;

import com.tradealert.notificationservice.observability.NotificationMetrics;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PendingNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PendingNotificationService.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationMetrics metrics;
    private final PresenceService presenceService;
    private final Tracer tracer;

    public PendingNotificationService(RedisTemplate<String, String> redisTemplate,
            SimpMessagingTemplate messagingTemplate,
            NotificationMetrics metrics,
            PresenceService presenceService,
            Tracer tracer) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.metrics = metrics;
        this.presenceService = presenceService;
        this.tracer = tracer;
    }

    /**
     * Send notification immediately if user is online, otherwise queue in Redis.
     */
    public void sendOrQueueNotification(Long userId, String eventJson) {
        Span span = tracer.spanBuilder("notification.send_or_queue").startSpan();
        try (var scope = span.makeCurrent()) {
            span.setAttribute("user.id", userId);
            boolean online = presenceService.isUserOnline(userId);
            span.setAttribute("user.online", online);
            if (online) {
                try {
                    messagingTemplate.convertAndSend("/topic/alerts/" + userId, eventJson);
                    metrics.incrementDelivered();
                    Span.current().addEvent("notification_delivered_online");
                } catch (Exception e) {
                    metrics.incrementFailed();
                    log.error("Failed to deliver notification to user {}: {}", userId, e.getMessage(), e);
                    queueNotification(userId, eventJson);
                }
            } else {
                queueNotification(userId, eventJson);
            }
        } catch (Exception exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, "Notification routing failed");
            throw new IllegalStateException("Notification routing failed", exception);
        } finally {
            span.end();
        }
    }

    /**
     * Queue a notification for a user if they are offline.
     * Stored in Redis list under "pending:{userId}".
     */
    public void queueNotification(Long userId, String eventJson) {
        String key = "pending:" + userId;
        try {
            redisTemplate.opsForList().rightPush(key, eventJson);
            metrics.incrementQueued();
            Span.current().addEvent("notification_queued_offline");
        } catch (Exception e) {
            metrics.incrementFailed();
            log.error("Failed to queue notification for user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Replay all pending notifications for a user when they reconnect.
     * Sends via WebSocket and clears the Redis list.
     */
    public void replayPendingNotifications(Long userId) {
        Span span = tracer.spanBuilder("notification.replay_pending").startSpan();
        try (var scope = span.makeCurrent()) {
            span.setAttribute("user.id", userId);
            String key = "pending:" + userId;
            List<String> pending = redisTemplate.opsForList().range(key, 0, -1);

            if (pending != null && !pending.isEmpty()) {
                String channel = "/topic/alerts/" + userId;
                for (String eventJson : pending) {
                    try {
                        messagingTemplate.convertAndSend(channel, eventJson);
                        metrics.incrementReplayed();
                    } catch (Exception e) {
                        metrics.incrementFailed();
                        log.error("Failed to replay notification for user {}: {}", userId, e.getMessage(), e);
                    }
                }
                redisTemplate.delete(key); // clear after replay
                span.setAttribute("notification.replayed_count", pending.size());
                span.addEvent("pending_notifications_replayed");
            } else {
                span.addEvent("no_pending_notifications");
            }
        } catch (Exception exception) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR, "Notification replay failed");
            throw new IllegalStateException("Notification replay failed", exception);
        } finally {
            span.end();
        }
    }
}
