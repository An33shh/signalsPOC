package com.signalspoc.api.dto.response;

import com.signalspoc.domain.entity.SyncLog;
import com.signalspoc.shared.model.Enums.ConnectorType;
import com.signalspoc.shared.model.Enums.SyncStatus;
import lombok.Builder;
import lombok.Data;
import java.time.Duration;
import java.time.LocalDateTime;

@Data
@Builder
public class SyncLogResponse {

    private Long id;
    private ConnectorType connectorType;
    private SyncStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationSeconds;
    private Integer projectsSynced;
    private Integer tasksSynced;
    private Integer usersSynced;
    private Integer commentsSynced;
    private String errorMessage;

    public static SyncLogResponse from(SyncLog log) {
        Long duration = null;
        if (log.getStartTime() != null && log.getEndTime() != null) {
            duration = Duration.between(log.getStartTime(), log.getEndTime()).getSeconds();
        }

        return SyncLogResponse.builder()
                .id(log.getId())
                .connectorType(log.getConnectorType())
                .status(log.getStatus())
                .startTime(log.getStartTime())
                .endTime(log.getEndTime())
                .durationSeconds(duration)
                .projectsSynced(log.getProjectsSynced())
                .tasksSynced(log.getTasksSynced())
                .usersSynced(log.getUsersSynced())
                .commentsSynced(log.getCommentsSynced())
                .errorMessage(log.getErrorMessage())
                .build();
    }
}
