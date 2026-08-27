package com.tradealert.alerts.repository;

import com.tradealert.alerts.model.Alert;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    @EntityGraph(attributePaths = { "user", "currencyPair" })
    @Query("SELECT a FROM Alert a WHERE a.currencyPair.symbol = :currencyPair "
            + "AND ((a.direction = 'ABOVE' AND a.targetRate <= :currentRate) "
            + "OR (a.direction = 'BELOW' AND a.targetRate >= :currentRate)) "
            + "AND a.notified = false")
    List<Alert> findActiveAlerts(String currencyPair, BigDecimal currentRate);

    @Modifying
    @Query("UPDATE Alert a SET a.notified = true, a.triggeredAt = :triggeredAt "
            + "WHERE a.id = :id AND a.notified = false")
    int claimForNotification(@Param("id") Long id, @Param("triggeredAt") Instant triggeredAt);
}
