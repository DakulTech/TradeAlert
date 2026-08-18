package com.tradealert.notificationservice.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradealert.notificationservice.events.AlertTriggeredEvent;
import com.tradealert.notificationservice.service.PresenceService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.RedisTemplate;

@Component
public class AlertConsumer {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final PresenceService presenceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AlertConsumer(SimpMessagingTemplate messagingTemplate,
            RedisTemplate<String, String> redisTemplate,
            PresenceService presenceService) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
        this.presenceService = presenceService;
    }

    @KafkaListener(topics = "alerts", groupId = "notification-service")
    public void consumeAlert(AlertTriggeredEvent event) {
        String channel = "/topic/alerts/" + event.getUserId();
        boolean userOnline = presenceService.isUserOnline(event.getUserId());

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
    }
}
