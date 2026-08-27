package com.tradealert.notifications.security;

import com.tradealert.identity.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);
    private final JwtUtil jwtUtil;

    public JwtHandshakeInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            log.debug("WebSocket handshake rejected: missing bearer token");
            return false;
        }

        String userId = jwtUtil.validateToken(authorization.substring(7));
        if (userId == null) {
            log.debug("WebSocket handshake rejected: invalid bearer token");
            return false;
        }

        try {
            attributes.put("userId", Long.valueOf(userId));
            return true;
        } catch (NumberFormatException exception) {
            log.debug("WebSocket handshake rejected: JWT subject is not a user id");
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // No post-handshake action is required.
    }
}
