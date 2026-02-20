package com.signalspoc.connector.pm.asana;

import com.signalspoc.connector.pm.asana.dto.*;
import com.signalspoc.connector.model.*;
import com.signalspoc.shared.model.Enums.ConnectorType;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class AsanaMapper {

    public ConnectorUser toConnectorUser(AsanaUserDto dto) {
        String name = dto.getName();
        if (name == null || name.isBlank()) {
            name = dto.getEmail() != null ? dto.getEmail() : "Unknown";
        }
        return ConnectorUser.builder()
                .externalId(dto.getGid())
                .sourceSystem(ConnectorType.ASANA)
                .name(name)
                .email(dto.getEmail())
                .isActive(true)
                .build();
    }

    public ConnectorProject toConnectorProject(AsanaProjectDto dto) {
        String status = null;
        if (dto.getCurrentStatus() != null) {
            status = dto.getCurrentStatus().getColor();
        }

        String ownerGid = null;
        if (dto.getOwner() != null) {
            ownerGid = dto.getOwner().getGid();
        }

        String projectName = dto.getName();
        if (projectName == null || projectName.isBlank()) {
            projectName = "Untitled Project";
        }

        return ConnectorProject.builder()
                .externalId(dto.getGid())
                .sourceSystem(ConnectorType.ASANA)
                .name(projectName)
                .status(status)
                .ownerExternalId(ownerGid)
                .createdAt(dto.getCreatedAt())
                .modifiedAt(dto.getModifiedAt())
                .build();
    }

    public ConnectorTask toConnectorTask(AsanaTaskDto dto, String projectGid) {
        String assigneeGid = null;
        if (dto.getAssignee() != null) {
            assigneeGid = dto.getAssignee().getGid();
        }

        String status = dto.isCompleted() ? "completed" : "open";

        LocalDateTime dueDate = null;
        if (dto.getDueOn() != null) {
            dueDate = dto.getDueOn().atStartOfDay();
        }

        String taskTitle = dto.getName();
        if (taskTitle == null || taskTitle.isBlank()) {
            taskTitle = "Untitled Task";
        }

        return ConnectorTask.builder()
                .externalId(dto.getGid())
                .sourceSystem(ConnectorType.ASANA)
                .projectExternalId(projectGid)
                .title(taskTitle)
                .description(dto.getNotes())
                .status(status)
                .assigneeExternalId(assigneeGid)
                .dueDate(dueDate)
                .createdAt(dto.getCreatedAt())
                .modifiedAt(dto.getModifiedAt())
                .build();
    }

    public ConnectorComment toConnectorComment(AsanaStoryDto dto, String taskGid) {
        String authorGid = null;
        if (dto.getCreatedBy() != null) {
            authorGid = dto.getCreatedBy().getGid();
        }

        String content = dto.getText();
        if (content == null) {
            content = "";
        }

        return ConnectorComment.builder()
                .externalId(dto.getGid())
                .sourceSystem(ConnectorType.ASANA)
                .taskExternalId(taskGid)
                .authorExternalId(authorGid)
                .content(content)
                .createdAt(dto.getCreatedAt())
                .build();
    }
}
