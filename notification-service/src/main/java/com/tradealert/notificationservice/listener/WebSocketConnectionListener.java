package com.tradealert.notificationservice.listener;

import com.tradealert.notificationservice.service.PendingNotificationService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketConnectionListener {

    private final PendingNotificationService pendingNotificationService;
    private final PresenceService presenceService;
    private final Tracer tracer;

    public WebSocketConnectionListener(PendingNotificationService pendingNotificationService,
            PresenceService presenceService,
            Tracer tracer) {
        this.pendingNotificationService = pendingNotificationService;
        this.presenceService = presenceService;
        this.tracer = tracer;
    }

    @EventListener
    public void handleWebSocketConnect(SessionConnectEvent event) {
        Span span = tracer.spanBuilder("notification.websocket_connect").startSpan();
        try (var scope = span.makeCurrent()) {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
            Object userIdObj = accessor.getSessionAttributes() == null
                    ? null
                    : accessor.getSessionAttributes().get("userId");
            if (userIdObj != null) {
                Long userId = userIdObj instanceof Long ? (Long) userIdObj : Long.parseLong(userIdObj.toString());
                presenceService.connect(userId, accessor.getSessionId());
                span.setAttribute("user.id", userId);
                span.addEvent("websocket_user_reconnected");
                pendingNotificationService.replayPendingNotifications(userId);
            }
        } finally {
            span.end();
        }
    }

    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
        Span span = tracer.spanBuilder("notification.websocket_disconnect_event").startSpan();
        try (var scope = span.makeCurrent()) {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
            Object userIdObj = accessor.getSessionAttributes() == null
                    ? null
                    : accessor.getSessionAttributes().get("userId");
            if (userIdObj != null) {
                Long userId = userIdObj instanceof Long ? (Long) userIdObj : Long.parseLong(userIdObj.toString());
                presenceService.disconnect(userId, accessor.getSessionId());
                span.setAttribute("user.id", userId);
            }
        } finally {
            span.end();
        }
    }
}
