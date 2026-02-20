package com.signalspoc.domain.entity;

import com.signalspoc.shared.model.Enums.ConnectorType;
import com.signalspoc.shared.model.Enums.SyncStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sync_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "connector_type", nullable = false, length = 50)
    private ConnectorType connectorType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SyncStatus status;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "projects_synced")
    private Integer projectsSynced;

    @Column(name = "tasks_synced")
    private Integer tasksSynced;

    @Column(name = "users_synced")
    private Integer usersSynced;

    @Column(name = "comments_synced")
    private Integer commentsSynced;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
