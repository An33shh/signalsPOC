package com.signalspoc.domain.entity;

import com.signalspoc.shared.model.Enums.ConnectorType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "sync_alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 50)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private AlertSeverity severity;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "ai_suggestion", columnDefinition = "TEXT")
    private String aiSuggestion;

    @Column(name = "ai_action_json", columnDefinition = "TEXT")
    private String aiActionJson;

    // Source platform info
    @Enumerated(EnumType.STRING)
    @Column(name = "source_system", length = 50)
    private ConnectorType sourceSystem;

    @Column(name = "source_id", length = 255)
    private String sourceId;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    // Target platform info (what's out of sync)
    @Enumerated(EnumType.STRING)
    @Column(name = "target_system", length = 50)
    private ConnectorType targetSystem;

    @Column(name = "target_id", length = 255)
    private String targetId;

    @Column(name = "target_url", length = 1000)
    private String targetUrl;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "is_resolved", nullable = false)
    @Builder.Default
    private Boolean isResolved = false;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum AlertType {
        PR_READY_TASK_NOT_UPDATED,      // PR is ready but task status not updated
        PR_MERGED_TASK_OPEN,            // PR merged but task still open
        TASK_COMPLETED_NO_PR,           // Task marked complete but no linked PR
        STALE_PR,                       // PR has been open too long
        MISSING_LINK,                   // PR doesn't link to any task
        STATUS_MISMATCH,                // Status doesn't match between platforms
        ASSIGNEE_MISMATCH               // Assignee different across platforms
    }

    public enum AlertSeverity {
        INFO,
        WARNING,
        CRITICAL
    }
}
