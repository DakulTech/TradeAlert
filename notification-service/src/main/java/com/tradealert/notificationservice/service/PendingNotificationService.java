package com.tradealert.notificationservice.service;

import com.tradealert.notificationservice.observability.NotificationMetrics;
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

    public PendingNotificationService(RedisTemplate<String, String> redisTemplate,
            SimpMessagingTemplate messagingTemplate,
            NotificationMetrics metrics,
            PresenceService presenceService) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.metrics = metrics;
        this.presenceService = presenceService;
    }

    /**
     * Send notification immediately if user is online, otherwise queue in Redis.
     */
    public void sendOrQueueNotification(Long userId, String eventJson) {
        if (presenceService.isUserOnline(userId)) {
            try {
                messagingTemplate.convertAndSend("/topic/alerts/" + userId, eventJson);
                metrics.incrementDelivered();
                log.info("Delivered notification to online user {}", userId);
            } catch (Exception e) {
                metrics.incrementFailed();
                log.error("Failed to deliver notification to user {}: {}", userId, e.getMessage(), e);
                queueNotification(userId, eventJson);
            }
        } else {
            queueNotification(userId, eventJson);
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
            log.info("Queued notification for user {}: {}", userId, eventJson);
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
            log.info("Replayed {} pending notifications for user {}", pending.size(), userId);
        } else {
            log.info("No pending notifications for user {}", userId);
        }
    }
}
