package com.signalspoc.connector.pm.linear;

import com.signalspoc.connector.model.ConnectorComment;
import com.signalspoc.connector.model.ConnectorProject;
import com.signalspoc.connector.model.ConnectorTask;
import com.signalspoc.connector.model.ConnectorUser;
import com.signalspoc.connector.pm.linear.dto.LinearCommentDto;
import com.signalspoc.connector.pm.linear.dto.LinearIssueDto;
import com.signalspoc.connector.pm.linear.dto.LinearProjectDto;
import com.signalspoc.connector.pm.linear.dto.LinearUserDto;
import com.signalspoc.shared.model.Enums.ConnectorType;
import com.signalspoc.shared.model.Enums.Priority;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Component
public class LinearMapper {

    public ConnectorUser toConnectorUser(LinearUserDto dto) {
        if (dto == null) return null;

        return ConnectorUser.builder()
                .externalId(dto.getId())
                .sourceSystem(ConnectorType.LINEAR)
                .name(dto.getDisplayName() != null ? dto.getDisplayName() : dto.getName())
                .email(dto.getEmail())
                .build();
    }

    public ConnectorProject toConnectorProject(LinearProjectDto dto) {
        if (dto == null) return null;

        return ConnectorProject.builder()
                .externalId(dto.getId())
                .sourceSystem(ConnectorType.LINEAR)
                .name(dto.getName())
                .description(dto.getDescription())
                .status(dto.getState())
                .ownerExternalId(dto.getLead() != null ? dto.getLead().getId() : null)
                .createdAt(toLocalDateTime(dto.getCreatedAt()))
                .modifiedAt(toLocalDateTime(dto.getUpdatedAt()))
                .build();
    }

    public ConnectorTask toConnectorTask(LinearIssueDto dto) {
        if (dto == null) return null;

        return ConnectorTask.builder()
                .externalId(dto.getId())
                .sourceSystem(ConnectorType.LINEAR)
                .title(dto.getIdentifier() + ": " + dto.getTitle())
                .description(dto.getDescription())
                .status(dto.getState() != null ? dto.getState().getName() : null)
                .priority(mapPriority(dto.getPriority()))
                .projectExternalId(dto.getProject() != null ? dto.getProject().getId() :
                                   (dto.getTeam() != null ? dto.getTeam().getId() : null))
                .assigneeExternalId(dto.getAssignee() != null ? dto.getAssignee().getId() : null)
                .dueDate(toLocalDateTime(dto.getDueDate()))
                .createdAt(toLocalDateTime(dto.getCreatedAt()))
                .modifiedAt(toLocalDateTime(dto.getUpdatedAt()))
                .externalUrl(dto.getUrl())
                .branchName(dto.getBranchName())
                .build();
    }

    public ConnectorComment toConnectorComment(LinearCommentDto dto, String issueId) {
        if (dto == null) return null;

        return ConnectorComment.builder()
                .externalId(dto.getId())
                .sourceSystem(ConnectorType.LINEAR)
                .content(dto.getBody())
                .taskExternalId(issueId != null ? issueId : dto.getIssueId())
                .authorExternalId(dto.getUser() != null ? dto.getUser().getId() : null)
                .createdAt(toLocalDateTime(dto.getCreatedAt()))
                .build();
    }

    private Priority mapPriority(Integer linearPriority) {
        if (linearPriority == null) return Priority.MEDIUM;

        return switch (linearPriority) {
            case 1 -> Priority.CRITICAL;
            case 2 -> Priority.HIGH;
            case 3 -> Priority.MEDIUM;
            case 4 -> Priority.LOW;
            default -> Priority.MEDIUM;
        };
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) return null;
        return offsetDateTime.toLocalDateTime();
    }
}
