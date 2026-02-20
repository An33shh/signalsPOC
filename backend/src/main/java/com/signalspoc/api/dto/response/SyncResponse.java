package com.signalspoc.api.dto.response;

import com.signalspoc.connector.model.SyncResult;
import com.signalspoc.shared.model.Enums.ConnectorType;
import com.signalspoc.shared.model.Enums.SyncStatus;
import lombok.Builder;
import lombok.Data;
import java.time.Duration;
import java.time.LocalDateTime;

@Data
@Builder
public class SyncResponse {

    private ConnectorType connectorType;
    private SyncStatus status;
    private SyncStatistics statistics;
    private LocalDateTime syncStartTime;
    private LocalDateTime syncEndTime;
    private Long durationSeconds;
    private String errorMessage;

    @Data
    @Builder
    public static class SyncStatistics {
        private int projectsCreated;
        private int projectsUpdated;
        private int tasksCreated;
        private int tasksUpdated;
        private int usersCreated;
        private int usersUpdated;
        private int commentsCreated;
        private int commentsUpdated;
    }

    public static SyncResponse from(SyncResult result) {
        Long duration = null;
        if (result.getSyncStartTime() != null && result.getSyncEndTime() != null) {
            duration = Duration.between(result.getSyncStartTime(), result.getSyncEndTime()).getSeconds();
        }

        return SyncResponse.builder()
                .connectorType(result.getConnectorType())
                .status(result.getStatus())
                .statistics(SyncStatistics.builder()
                        .projectsCreated(result.getProjectsCreated())
                        .projectsUpdated(result.getProjectsUpdated())
                        .tasksCreated(result.getTasksCreated())
                        .tasksUpdated(result.getTasksUpdated())
                        .usersCreated(result.getUsersCreated())
                        .usersUpdated(result.getUsersUpdated())
                        .commentsCreated(result.getCommentsCreated())
                        .commentsUpdated(result.getCommentsUpdated())
                        .build())
                .syncStartTime(result.getSyncStartTime())
                .syncEndTime(result.getSyncEndTime())
                .durationSeconds(duration)
                .errorMessage(result.getErrorMessage())
                .build();
    }
}
