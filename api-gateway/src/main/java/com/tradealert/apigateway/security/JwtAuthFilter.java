package com.tradealert.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;

@Component
public class JwtAuthFilter implements WebFilter {

    @Value("${jwt.secret}")
    private String secret;
    private final Tracer tracer;
    private final Counter rejectedRequests;
    private final Counter authenticatedRequests;

    public JwtAuthFilter(Tracer tracer, MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.rejectedRequests = Counter.builder("gateway_auth_rejections_total")
                .description("Requests rejected by gateway JWT authentication")
                .register(meterRegistry);
        this.authenticatedRequests = Counter.builder("gateway_authenticated_requests_total")
                .description("Requests accepted by gateway JWT authentication")
                .register(meterRegistry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Span span = tracer.spanBuilder("gateway.authenticate_request").startSpan();
        span.setAttribute("http.method", exchange.getRequest().getMethod().name());
        span.setAttribute("http.route", exchange.getRequest().getPath().value());
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            rejectedRequests.increment();
            span.setStatus(StatusCode.ERROR, "Missing bearer token");
            span.end();
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token.replace("Bearer ", ""))
                    .getPayload();

            String role = claims.get("role", String.class);
            if (role == null || role.isEmpty()) {
                rejectedRequests.increment();
                span.setStatus(StatusCode.ERROR, "Missing token role");
                span.end();
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }

            authenticatedRequests.increment();
            span.setAttribute("user.role", role);
            return chain.filter(exchange).doFinally(signal -> span.end());
        } catch (Exception e) {
            rejectedRequests.increment();
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, "Invalid bearer token");
            span.end();
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }
}
