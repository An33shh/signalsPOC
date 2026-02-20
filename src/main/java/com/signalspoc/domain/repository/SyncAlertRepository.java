package com.signalspoc.domain.repository;

import com.signalspoc.domain.entity.SyncAlert;
import com.signalspoc.shared.model.Enums.ConnectorType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SyncAlertRepository extends JpaRepository<SyncAlert, Long> {

    Page<SyncAlert> findByIsResolvedFalseOrderByCreatedAtDesc(Pageable pageable);

    Page<SyncAlert> findByIsReadFalseAndIsResolvedFalseOrderByCreatedAtDesc(Pageable pageable);

    List<SyncAlert> findBySourceSystemAndSourceIdAndIsResolvedFalse(
            ConnectorType sourceSystem, String sourceId);

    Optional<SyncAlert> findBySourceSystemAndSourceIdAndTargetSystemAndTargetIdAndAlertTypeAndIsResolvedFalse(
            ConnectorType sourceSystem, String sourceId,
            ConnectorType targetSystem, String targetId,
            SyncAlert.AlertType alertType);

    long countByIsReadFalseAndIsResolvedFalse();

    @Modifying
    @Query("UPDATE SyncAlert a SET a.isRead = true WHERE a.id = :id")
    void markAsRead(Long id);

    @Modifying
    @Query("UPDATE SyncAlert a SET a.isResolved = true, a.resolvedAt = :now WHERE a.id = :id")
    void resolve(Long id, LocalDateTime now);

    // Used by AiEnrichmentWorker to patch an alert after async Ollama call
    @Modifying
    @Query("UPDATE SyncAlert a SET a.aiSuggestion = :suggestion, a.aiActionJson = :actionJson WHERE a.id = :id")
    void updateAiEnrichment(Long id, String suggestion, String actionJson);

    // Used by AiAnalysisScheduler reconciliation to find alerts missed by the event worker
    List<SyncAlert> findByAiSuggestionIsNullAndIsResolvedFalseOrderByCreatedAtAsc();

}
