package com.signalspoc.connector.model;

import com.signalspoc.shared.model.Enums.ConnectorType;
import com.signalspoc.shared.model.Enums.SyncStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class SyncResult {

    private ConnectorType connectorType;
    private SyncStatus status;
    private int projectsCreated;
    private int projectsUpdated;
    private int tasksCreated;
    private int tasksUpdated;
    private int usersCreated;
    private int usersUpdated;
    private int commentsCreated;
    private int commentsUpdated;
    private String errorMessage;
    private LocalDateTime syncStartTime;
    private LocalDateTime syncEndTime;

    public static SyncResult empty(ConnectorType connectorType) {
        return SyncResult.builder()
                .connectorType(connectorType)
                .status(SyncStatus.SUCCESS)
                .syncStartTime(LocalDateTime.now())
                .build();
    }

    public SyncResult merge(SyncResult other) {
        this.projectsCreated += other.projectsCreated;
        this.projectsUpdated += other.projectsUpdated;
        this.tasksCreated += other.tasksCreated;
        this.tasksUpdated += other.tasksUpdated;
        this.usersCreated += other.usersCreated;
        this.usersUpdated += other.usersUpdated;
        this.commentsCreated += other.commentsCreated;
        this.commentsUpdated += other.commentsUpdated;
        return this;
    }
}
