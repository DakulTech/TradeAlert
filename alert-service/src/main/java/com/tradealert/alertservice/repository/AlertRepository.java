package com.tradealert.alertservice.repository;

import com.tradealert.alertservice.model.Alert;
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
    @Query("SELECT a FROM Alert a WHERE a.currencyPair.symbol = :currencyPair " +
            "AND ((a.direction = 'ABOVE' AND a.targetRate <= :currentRate) " +
            "OR (a.direction = 'BELOW' AND a.targetRate >= :currentRate)) " +
            "AND a.notified = false")
    List<Alert> findActiveAlerts(String currencyPair, BigDecimal currentRate);

    @Modifying
    // Custom method to claim an alert for notification
    @Query(value = "UPDATE alerts SET notified = true, triggered_at = :triggeredAt " +
            "WHERE id = :id AND notified = false", nativeQuery = true)
    int claimForNotification(@Param("id") Long id, @Param("triggeredAt") Instant triggeredAt);
}
