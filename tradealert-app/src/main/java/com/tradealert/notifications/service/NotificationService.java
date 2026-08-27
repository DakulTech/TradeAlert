package com.tradealert.notifications.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradealert.notifications.model.AlertNotification;
import com.tradealert.notifications.observability.NotificationMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final RedisTemplate<String, String> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;
    private final NotificationMetrics metrics;
    private final ObjectMapper objectMapper;

    public NotificationService(RedisTemplate<String, String> redisTemplate,
            SimpMessagingTemplate messagingTemplate, PresenceService presenceService,
            NotificationMetrics metrics, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.presenceService = presenceService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    public void sendOrQueue(AlertNotification notification) {
        Long userId = notification.userId();
        if (presenceService.isUserOnline(userId)) {
            try {
                messagingTemplate.convertAndSend(channel(userId), notification);
                metrics.incrementDelivered();
                return;
            } catch (Exception exception) {
                log.warn("WebSocket delivery failed for user {}; queuing notification", userId, exception);
                metrics.incrementFailed();
            }
        }
        queue(notification);
    }

    public void queue(AlertNotification notification) {
        try {
            redisTemplate.opsForList().rightPush(queueKey(notification.userId()), serialize(notification));
            metrics.incrementQueued();
        } catch (Exception exception) {
            metrics.incrementFailed();
            throw new IllegalStateException("Could not queue notification", exception);
        }
    }

    public void replayPending(Long userId) {
        String key = queueKey(userId);
        List<String> pending = redisTemplate.opsForList().range(key, 0, -1);
        if (pending == null || pending.isEmpty()) {
            return;
        }
        for (String event : pending) {
            messagingTemplate.convertAndSend(channel(userId), event);
            metrics.incrementReplayed();
        }
        redisTemplate.delete(key);
    }

    private String serialize(AlertNotification notification) {
        try {
            return objectMapper.writeValueAsString(notification);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize notification", exception);
        }
    }

    private String queueKey(Long userId) {
        return "pending:" + userId;
    }

    private String channel(Long userId) {
        return "/topic/alerts/" + userId;
    }
}
