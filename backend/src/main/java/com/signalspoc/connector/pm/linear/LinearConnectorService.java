package com.signalspoc.connector.pm.linear;

import com.signalspoc.connector.pm.api.PmConnectorService;
import com.signalspoc.connector.model.ConnectorComment;
import com.signalspoc.connector.model.ConnectorProject;
import com.signalspoc.connector.model.ConnectorTask;
import com.signalspoc.connector.model.ConnectorUser;
import com.signalspoc.connector.model.SyncResult;
import com.signalspoc.domain.service.CommentService;
import com.signalspoc.domain.service.ProjectService;
import com.signalspoc.domain.service.TaskService;
import com.signalspoc.domain.service.UserService;
import com.signalspoc.connector.pm.linear.dto.LinearCommentDto;
import com.signalspoc.connector.pm.linear.dto.LinearIssueDto;
import com.signalspoc.connector.pm.linear.dto.LinearProjectDto;
import com.signalspoc.connector.pm.linear.dto.LinearUserDto;
import com.signalspoc.shared.model.Enums.ConnectorType;
import com.signalspoc.shared.model.Enums.SyncStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@ConditionalOnProperty(name = "connectors.linear.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class LinearConnectorService implements PmConnectorService {

    private final LinearApiClient apiClient;
    private final LinearMapper mapper;
    private final UserService userService;
    private final ProjectService projectService;
    private final TaskService taskService;
    private final CommentService commentService;

    @Override
    public ConnectorType getConnectorType() {
        return ConnectorType.LINEAR;
    }

    @Override
    public boolean testConnection() {
        return apiClient.testConnection();
    }

    @Override
    public SyncResult syncAll() {
        LocalDateTime startTime = LocalDateTime.now();
        SyncResult result = SyncResult.empty(ConnectorType.LINEAR);
        result.setSyncStartTime(startTime);

        try {
            result.merge(syncUsers());
            result.merge(syncProjects());
            result.merge(syncTasks());
            result.merge(syncComments());

            result.setStatus(SyncStatus.SUCCESS);
            result.setSyncEndTime(LocalDateTime.now());

        } catch (Exception e) {
            log.error("Error during Linear sync", e);
            result.setStatus(SyncStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            result.setSyncEndTime(LocalDateTime.now());
        }

        return result;
    }

    @Override
    public SyncResult syncUsers() {
        log.info("Syncing users from Linear");
        SyncResult result = SyncResult.empty(ConnectorType.LINEAR);

        List<LinearUserDto> users = apiClient.getAllUsers();
        List<ConnectorUser> connectorUsers = users.stream()
                .map(mapper::toConnectorUser)
                .toList();

        List<UserService.UpsertResult<com.signalspoc.domain.entity.User>> results =
                userService.upsertAll(connectorUsers);

        result.setUsersCreated((int) results.stream().filter(UserService.UpsertResult::created).count());
        result.setUsersUpdated((int) results.stream().filter(r -> !r.created()).count());

        log.info("Synced {} users from Linear ({} created, {} updated)",
                results.size(), result.getUsersCreated(), result.getUsersUpdated());

        return result;
    }

    @Override
    public SyncResult syncProjects() {
        log.info("Syncing projects from Linear");
        SyncResult result = SyncResult.empty(ConnectorType.LINEAR);

        List<LinearProjectDto> projects = apiClient.getAllProjects();
        List<ConnectorProject> connectorProjects = projects.stream()
                .map(mapper::toConnectorProject)
                .toList();

        List<UserService.UpsertResult<com.signalspoc.domain.entity.Project>> results =
                projectService.upsertAll(connectorProjects);

        result.setProjectsCreated((int) results.stream().filter(UserService.UpsertResult::created).count());
        result.setProjectsUpdated((int) results.stream().filter(r -> !r.created()).count());

        log.info("Synced {} projects from Linear ({} created, {} updated)",
                results.size(), result.getProjectsCreated(), result.getProjectsUpdated());

        return result;
    }

    @Override
    public SyncResult syncTasks() {
        log.info("Syncing issues from Linear");
        SyncResult result = SyncResult.empty(ConnectorType.LINEAR);

        List<LinearIssueDto> issues = apiClient.getAllIssues();
        List<ConnectorTask> connectorTasks = issues.stream()
                .map(mapper::toConnectorTask)
                .toList();

        List<UserService.UpsertResult<com.signalspoc.domain.entity.Task>> results =
                taskService.upsertAll(connectorTasks);

        result.setTasksCreated((int) results.stream().filter(UserService.UpsertResult::created).count());
        result.setTasksUpdated((int) results.stream().filter(r -> !r.created()).count());

        log.info("Synced {} issues from Linear ({} created, {} updated)",
                results.size(), result.getTasksCreated(), result.getTasksUpdated());

        return result;
    }

    @Override
    public SyncResult syncComments() {
        log.info("Syncing comments from Linear");
        SyncResult result = SyncResult.empty(ConnectorType.LINEAR);

        List<LinearCommentDto> comments = apiClient.getAllComments();
        List<ConnectorComment> connectorComments = comments.stream()
                .map(c -> mapper.toConnectorComment(c, c.getIssueId()))
                .toList();

        List<UserService.UpsertResult<com.signalspoc.domain.entity.Comment>> results =
                commentService.upsertAll(connectorComments);

        result.setCommentsCreated((int) results.stream().filter(UserService.UpsertResult::created).count());
        result.setCommentsUpdated((int) results.stream().filter(r -> !r.created()).count());

        log.info("Synced {} comments from Linear ({} created, {} updated)",
                results.size(), result.getCommentsCreated(), result.getCommentsUpdated());

        return result;
    }

    @Override
    public void updateTaskStatus(String externalId, String status) {
        apiClient.updateIssueStatus(externalId, status);
    }

    @Override
    public void addComment(String entityId, String comment) {
        apiClient.addIssueComment(entityId, comment);
    }

    @Override
    public void completeTask(String externalId) {
        apiClient.updateIssueStatus(externalId, "Done");
    }
}
