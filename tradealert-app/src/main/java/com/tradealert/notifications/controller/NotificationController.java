package com.tradealert.notifications.controller;

import com.tradealert.notifications.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationController(NotificationService notificationService,
            SimpMessagingTemplate messagingTemplate) {
        this.notificationService = notificationService;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping("/replay/{userId}")
    public ResponseEntity<String> replayPending(@PathVariable Long userId) {
        notificationService.replayPending(userId);
        return ResponseEntity.ok("Pending notifications replayed for user " + userId);
    }

    @GetMapping("/ping/{userId}")
    public ResponseEntity<String> sendPing(@PathVariable Long userId) {
        Instant now = Instant.now();
        messagingTemplate.convertAndSend("/topic/ping/" + userId, now.toString());
        return ResponseEntity.ok("Ping sent to user " + userId + " at " + now);
    }
}
