package com.tradealert.notificationservice.controller;

import com.tradealert.notificationservice.route.NotificationRoutes;
import com.tradealert.notificationservice.service.PendingNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping(NotificationRoutes.BASE)
public class NotificationController {

    private final PendingNotificationService pendingNotificationService;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationController(PendingNotificationService pendingNotificationService,
            SimpMessagingTemplate messagingTemplate) {
        this.pendingNotificationService = pendingNotificationService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Endpoint to replay pending notifications for a user.
     * Called when the frontend reconnects after being offline.
     */
    @PostMapping("/replay/{userId}")
    public ResponseEntity<String> replayPending(@PathVariable Long userId) {
        pendingNotificationService.replayPendingNotifications(userId);
        return ResponseEntity.ok("Pending notifications replayed for user " + userId);
    }

    /**
     * Endpoint to send a ping to the client for latency measurement.
     * The client should respond with a pong and timestamp.
     */
    @GetMapping("/ping/{userId}")
    public ResponseEntity<String> sendPing(@PathVariable Long userId) {
        Instant now = Instant.now();
        messagingTemplate.convertAndSend("/topic/ping/" + userId, now.toString());
        return ResponseEntity.ok("Ping sent to user " + userId + " at " + now);
    }
}
