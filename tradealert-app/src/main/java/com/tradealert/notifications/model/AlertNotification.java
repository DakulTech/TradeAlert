package com.tradealert.notifications.model;

import java.math.BigDecimal;
import java.time.Instant;

public record AlertNotification(
        Long alertId,
        Long userId,
        String currencyPair,
        BigDecimal targetRate,
        BigDecimal triggeredRate,
        Instant triggeredAt) {
}
