package com.signalspoc.api.dto.response;

import com.signalspoc.domain.entity.Project;
import com.signalspoc.shared.model.Enums.ConnectorType;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ProjectResponse {

    private Long id;
    private String externalId;
    private ConnectorType sourceSystem;
    private String name;
    private String description;
    private String status;
    private UserSummary owner;
    private LocalDateTime externalCreatedAt;
    private LocalDateTime externalModifiedAt;
    private LocalDateTime syncedAt;
    private LocalDateTime lastSyncedAt;

    @Data
    @Builder
    public static class UserSummary {
        private Long id;
        private String name;
        private String email;
    }

    public static ProjectResponse from(Project project) {
        UserSummary ownerSummary = null;
        if (project.getOwner() != null) {
            ownerSummary = UserSummary.builder()
                    .id(project.getOwner().getId())
                    .name(project.getOwner().getName())
                    .email(project.getOwner().getEmail())
                    .build();
        }

        return ProjectResponse.builder()
                .id(project.getId())
                .externalId(project.getExternalId())
                .sourceSystem(project.getSourceSystem())
                .name(project.getName())
                .description(project.getDescription())
                .status(project.getStatus())
                .owner(ownerSummary)
                .externalCreatedAt(project.getExternalCreatedAt())
                .externalModifiedAt(project.getExternalModifiedAt())
                .syncedAt(project.getSyncedAt())
                .lastSyncedAt(project.getLastSyncedAt())
                .build();
    }
}
