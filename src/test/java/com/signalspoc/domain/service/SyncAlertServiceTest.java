package com.signalspoc.domain.service;

import com.signalspoc.domain.entity.SyncAlert;
import com.signalspoc.domain.repository.SyncAlertRepository;
import com.signalspoc.shared.model.Enums.ConnectorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncAlertServiceTest {

    @Mock
    private SyncAlertRepository alertRepository;

    @InjectMocks
    private SyncAlertService alertService;

    // ─── createAlert ──────────────────────────────────────────────────────────

    @Test
    void createAlert_savesAndReturnsAlertWhenNoDuplicateExists() {
        SyncAlert incoming = buildAlert(SyncAlert.AlertType.PR_MERGED_TASK_OPEN);
        when(alertRepository.findBySourceSystemAndSourceIdAndTargetSystemAndTargetIdAndAlertTypeAndIsResolvedFalse(
                any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(alertRepository.save(incoming)).thenReturn(incoming);

        SyncAlert result = alertService.createAlert(incoming);

        assertThat(result).isSameAs(incoming);
        verify(alertRepository).save(incoming);
    }

    @Test
    void createAlert_returnsDuplicateAlertWithoutSaving() {
        SyncAlert incoming = buildAlert(SyncAlert.AlertType.PR_MERGED_TASK_OPEN);
        SyncAlert existing = buildAlert(SyncAlert.AlertType.PR_MERGED_TASK_OPEN);
        existing.setId(42L);

        when(alertRepository.findBySourceSystemAndSourceIdAndTargetSystemAndTargetIdAndAlertTypeAndIsResolvedFalse(
                any(), any(), any(), any(), any())).thenReturn(Optional.of(existing));

        SyncAlert result = alertService.createAlert(incoming);

        assertThat(result).isSameAs(existing);
        assertThat(result.getId()).isEqualTo(42L);
        verify(alertRepository, never()).save(any());
    }

    @Test
    void createAlert_deduplicationUsesAllKeyFields() {
        SyncAlert incoming = buildAlert(SyncAlert.AlertType.STALE_PR);
        when(alertRepository.findBySourceSystemAndSourceIdAndTargetSystemAndTargetIdAndAlertTypeAndIsResolvedFalse(
                eq(ConnectorType.GITHUB), eq("pr-1"), eq(ConnectorType.ASANA), eq("task-1"),
                eq(SyncAlert.AlertType.STALE_PR)))
                .thenReturn(Optional.empty());
        when(alertRepository.save(any())).thenReturn(incoming);

        alertService.createAlert(incoming);

        verify(alertRepository).findBySourceSystemAndSourceIdAndTargetSystemAndTargetIdAndAlertTypeAndIsResolvedFalse(
                ConnectorType.GITHUB, "pr-1", ConnectorType.ASANA, "task-1", SyncAlert.AlertType.STALE_PR);
    }

    // ─── markAsRead / resolve ──────────────────────────────────────────────────

    @Test
    void markAsRead_delegatesToRepository() {
        alertService.markAsRead(99L);
        verify(alertRepository).markAsRead(99L);
    }

    @Test
    void resolve_delegatesToRepositoryWithTimestamp() {
        alertService.resolve(5L);
        verify(alertRepository).resolve(eq(5L), any(LocalDateTime.class));
    }

    @Test
    void getUnreadCount_delegatesToRepository() {
        when(alertRepository.countByIsReadFalseAndIsResolvedFalse()).thenReturn(7L);
        assertThat(alertService.getUnreadCount()).isEqualTo(7L);
    }

    // ─── resolveAlertsForSource ────────────────────────────────────────────────

    @Test
    void resolveAlertsForSource_resolvesAndSavesAllMatchingAlerts() {
        SyncAlert a1 = buildAlert(SyncAlert.AlertType.STALE_PR);
        SyncAlert a2 = buildAlert(SyncAlert.AlertType.MISSING_LINK);
        when(alertRepository.findBySourceSystemAndSourceIdAndIsResolvedFalse(ConnectorType.GITHUB, "pr-1"))
                .thenReturn(List.of(a1, a2));

        alertService.resolveAlertsForSource(ConnectorType.GITHUB, "pr-1");

        assertThat(a1.getIsResolved()).isTrue();
        assertThat(a1.getResolvedAt()).isNotNull();
        assertThat(a2.getIsResolved()).isTrue();
        assertThat(a2.getResolvedAt()).isNotNull();
        verify(alertRepository).save(a1);
        verify(alertRepository).save(a2);
    }

    @Test
    void resolveAlertsForSource_doesNothingWhenNoMatchingAlerts() {
        when(alertRepository.findBySourceSystemAndSourceIdAndIsResolvedFalse(any(), any()))
                .thenReturn(List.of());

        alertService.resolveAlertsForSource(ConnectorType.GITHUB, "pr-missing");

        verify(alertRepository, never()).save(any());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private SyncAlert buildAlert(SyncAlert.AlertType type) {
        return SyncAlert.builder()
                .alertType(type)
                .severity(SyncAlert.AlertSeverity.WARNING)
                .title("Test Alert")
                .sourceSystem(ConnectorType.GITHUB)
                .sourceId("pr-1")
                .targetSystem(ConnectorType.ASANA)
                .targetId("task-1")
                .build();
    }
}
