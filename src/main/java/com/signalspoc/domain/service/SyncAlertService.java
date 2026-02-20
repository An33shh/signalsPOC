package com.signalspoc.domain.service;

import com.signalspoc.domain.entity.SyncAlert;
import com.signalspoc.domain.repository.SyncAlertRepository;
import com.signalspoc.shared.model.Enums.ConnectorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncAlertService {

    private final SyncAlertRepository alertRepository;

    @Transactional
    public SyncAlert createAlert(SyncAlert alert) {
        // Check if same alert already exists
        Optional<SyncAlert> existing = alertRepository
                .findBySourceSystemAndSourceIdAndTargetSystemAndTargetIdAndAlertTypeAndIsResolvedFalse(
                        alert.getSourceSystem(), alert.getSourceId(),
                        alert.getTargetSystem(), alert.getTargetId(),
                        alert.getAlertType()
                );

        if (existing.isPresent()) {
            log.debug("Alert already exists: {}", existing.get().getId());
            return existing.get();
        }

        log.info("Creating new sync alert: {} - {}", alert.getAlertType(), alert.getTitle());
        return alertRepository.save(alert);
    }

    @Transactional(readOnly = true)
    public Page<SyncAlert> getUnresolvedAlerts(Pageable pageable) {
        return alertRepository.findByIsResolvedFalseOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<SyncAlert> getUnreadAlerts(Pageable pageable) {
        return alertRepository.findByIsReadFalseAndIsResolvedFalseOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        return alertRepository.countByIsReadFalseAndIsResolvedFalse();
    }

    @Transactional
    public void markAsRead(Long alertId) {
        alertRepository.markAsRead(alertId);
    }

    @Transactional
    public void resolve(Long alertId) {
        alertRepository.resolve(alertId, LocalDateTime.now());
    }

    @Transactional
    public void resolveAlertsForSource(ConnectorType sourceSystem, String sourceId) {
        alertRepository.findBySourceSystemAndSourceIdAndIsResolvedFalse(sourceSystem, sourceId)
                .forEach(alert -> {
                    alert.setIsResolved(true);
                    alert.setResolvedAt(LocalDateTime.now());
                    alertRepository.save(alert);
                });
    }
}
