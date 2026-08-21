package com.tradealert.alertservice.service;

import com.tradealert.alertservice.model.Alert;
import com.tradealert.alertservice.repository.AlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AlertService {

    private final AlertRepository alertRepository;

    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    public List<Alert> getAllAlerts() {
        return alertRepository.findAll();
    }

    public Alert createAlert(Alert alert) {
        alert.setNotified(false);
        alert.setCreatedAt(Instant.now());
        return alertRepository.save(alert);
    }

    @Transactional
    public Alert updateAlert(Long id, Alert updatedAlert) {
        Alert existing = alertRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + id));

        existing.setTargetRate(updatedAlert.getTargetRate());
        existing.setDirection(updatedAlert.getDirection());
        existing.setCurrencyPair(updatedAlert.getCurrencyPair());
        existing.setUser(updatedAlert.getUser());
        return alertRepository.save(existing);
    }

    @Transactional
    public void deleteAlert(Long id) {
        if (!alertRepository.existsById(id)) {
            throw new IllegalArgumentException("Alert not found: " + id);
        }
        alertRepository.deleteById(id);
    }

    @Transactional
    public boolean claimForNotification(Long alertId, Instant triggeredAt) {
        return alertRepository.claimForNotification(alertId, triggeredAt) == 1;
    }
}
