package com.signalspoc.api.dto.response;

import com.signalspoc.domain.entity.Task;
import com.signalspoc.shared.model.Enums.ConnectorType;
import com.signalspoc.shared.model.Enums.Priority;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class TaskResponse {

    private Long id;
    private String externalId;
    private ConnectorType sourceSystem;
    private ProjectSummary project;
    private String title;
    private String description;
    private String status;
    private Priority priority;
    private UserSummary assignee;
    private LocalDateTime dueDate;
    private LocalDateTime externalCreatedAt;
    private LocalDateTime externalModifiedAt;
    private LocalDateTime syncedAt;
    private LocalDateTime lastSyncedAt;

    @Data
    @Builder
    public static class ProjectSummary {
        private Long id;
        private String name;
    }

    @Data
    @Builder
    public static class UserSummary {
        private Long id;
        private String name;
        private String email;
    }

    public static TaskResponse from(Task task) {
        ProjectSummary projectSummary = ProjectSummary.builder()
                .id(task.getProject().getId())
                .name(task.getProject().getName())
                .build();

        UserSummary assigneeSummary = null;
        if (task.getAssignee() != null) {
            assigneeSummary = UserSummary.builder()
                    .id(task.getAssignee().getId())
                    .name(task.getAssignee().getName())
                    .email(task.getAssignee().getEmail())
                    .build();
        }

        return TaskResponse.builder()
                .id(task.getId())
                .externalId(task.getExternalId())
                .sourceSystem(task.getSourceSystem())
                .project(projectSummary)
                .title(task.getTitle())
                .description(task.getDescription())
                .status(task.getStatus())
                .priority(task.getPriority())
                .assignee(assigneeSummary)
                .dueDate(task.getDueDate())
                .externalCreatedAt(task.getExternalCreatedAt())
                .externalModifiedAt(task.getExternalModifiedAt())
                .syncedAt(task.getSyncedAt())
                .lastSyncedAt(task.getLastSyncedAt())
                .build();
    }
}
