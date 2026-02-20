package com.signalspoc.connector.pm.asana;

import com.signalspoc.connector.pm.asana.dto.*;
import com.signalspoc.connector.pm.api.PmConnectorService;
import com.signalspoc.connector.model.*;
import com.signalspoc.domain.service.*;
import com.signalspoc.shared.model.Enums.ConnectorType;
import com.signalspoc.shared.model.Enums.SyncStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(name = "connectors.asana.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AsanaConnectorService implements PmConnectorService {

    private final AsanaApiClient apiClient;
    private final AsanaMapper mapper;
    private final UserService userService;
    private final ProjectService projectService;
    private final TaskService taskService;
    private final CommentService commentService;

    @Override
    public ConnectorType getConnectorType() {
        return ConnectorType.ASANA;
    }

    @Override
    public SyncResult syncAll() {
        LocalDateTime startTime = LocalDateTime.now();
        SyncResult result = SyncResult.empty(ConnectorType.ASANA);
        result.setSyncStartTime(startTime);

        try {
            // Sync in order: Users -> Projects -> Tasks -> Comments
            result.merge(syncUsers());
            result.merge(syncProjects());
            result.merge(syncTasks());
            result.merge(syncComments());

            result.setStatus(SyncStatus.SUCCESS);
            result.setSyncEndTime(LocalDateTime.now());

        } catch (Exception e) {
            log.error("Error during Asana sync", e);
            result.setStatus(SyncStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            result.setSyncEndTime(LocalDateTime.now());
        }

        return result;
    }

    @Override
    public SyncResult syncUsers() {
        log.info("Syncing users from Asana (extracting from projects and tasks)");
        SyncResult result = SyncResult.empty(ConnectorType.ASANA);

        // Instead of fetching ALL workspace users (can be thousands in large orgs),
        // only sync users referenced by tasks and projects we care about.
        Set<String> userGids = new HashSet<>();
        List<AsanaProjectDto> projects = apiClient.getAllProjects();

        for (AsanaProjectDto project : projects) {
            if (project.getOwner() != null && project.getOwner().getGid() != null) {
                userGids.add(project.getOwner().getGid());
            }
            List<AsanaTaskDto> tasks = apiClient.getTasksForProject(project.getGid());
            for (AsanaTaskDto task : tasks) {
                if (task.getAssignee() != null && task.getAssignee().getGid() != null) {
                    userGids.add(task.getAssignee().getGid());
                }
            }
        }

        // Fetch full user details for each referenced user
        List<ConnectorUser> connectorUsers = userGids.stream()
                .map(gid -> {
                    try {
                        AsanaUserDto user = apiClient.getUser(gid);
                        return user != null ? mapper.toConnectorUser(user) : null;
                    } catch (Exception e) {
                        log.warn("Failed to fetch Asana user {}: {}", gid, e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<UserService.UpsertResult<com.signalspoc.domain.entity.User>> results =
                userService.upsertAll(connectorUsers);

        result.setUsersCreated((int) results.stream().filter(UserService.UpsertResult::created).count());
        result.setUsersUpdated((int) results.stream().filter(r -> !r.created()).count());

        log.info("Synced {} users from Asana ({} created, {} updated)",
                results.size(), result.getUsersCreated(), result.getUsersUpdated());

        return result;
    }

    @Override
    public SyncResult syncProjects() {
        log.info("Syncing projects from Asana");
        SyncResult result = SyncResult.empty(ConnectorType.ASANA);

        List<AsanaProjectDto> projects = apiClient.getAllProjects();
        List<ConnectorProject> connectorProjects = projects.stream()
                .map(mapper::toConnectorProject)
                .toList();

        List<UserService.UpsertResult<com.signalspoc.domain.entity.Project>> results =
                projectService.upsertAll(connectorProjects);

        result.setProjectsCreated((int) results.stream().filter(UserService.UpsertResult::created).count());
        result.setProjectsUpdated((int) results.stream().filter(r -> !r.created()).count());

        log.info("Synced {} projects from Asana ({} created, {} updated)",
                results.size(), result.getProjectsCreated(), result.getProjectsUpdated());

        return result;
    }

    @Override
    public SyncResult syncTasks() {
        log.info("Syncing tasks from Asana");
        SyncResult result = SyncResult.empty(ConnectorType.ASANA);

        List<AsanaProjectDto> projects = apiClient.getAllProjects();
        int totalCreated = 0;
        int totalUpdated = 0;

        for (AsanaProjectDto project : projects) {
            List<AsanaTaskDto> tasks = apiClient.getTasksForProject(project.getGid());
            List<ConnectorTask> connectorTasks = tasks.stream()
                    .map(t -> mapper.toConnectorTask(t, project.getGid()))
                    .toList();

            List<UserService.UpsertResult<com.signalspoc.domain.entity.Task>> results =
                    taskService.upsertAll(connectorTasks);

            totalCreated += (int) results.stream().filter(UserService.UpsertResult::created).count();
            totalUpdated += (int) results.stream().filter(r -> !r.created()).count();
        }

        result.setTasksCreated(totalCreated);
        result.setTasksUpdated(totalUpdated);

        log.info("Synced {} tasks from Asana ({} created, {} updated)",
                totalCreated + totalUpdated, totalCreated, totalUpdated);

        return result;
    }

    @Override
    public SyncResult syncComments() {
        log.info("Syncing comments from Asana");
        SyncResult result = SyncResult.empty(ConnectorType.ASANA);

        List<AsanaProjectDto> projects = apiClient.getAllProjects();
        int totalCreated = 0;
        int totalUpdated = 0;

        for (AsanaProjectDto project : projects) {
            List<AsanaTaskDto> tasks = apiClient.getTasksForProject(project.getGid());

            for (AsanaTaskDto task : tasks) {
                List<AsanaStoryDto> stories = apiClient.getStoriesForTask(task.getGid());

                // Filter only comment stories
                List<ConnectorComment> connectorComments = stories.stream()
                        .filter(s -> "comment".equals(s.getType()))
                        .map(s -> mapper.toConnectorComment(s, task.getGid()))
                        .toList();

                List<UserService.UpsertResult<com.signalspoc.domain.entity.Comment>> results =
                        commentService.upsertAll(connectorComments);

                totalCreated += (int) results.stream().filter(UserService.UpsertResult::created).count();
                totalUpdated += (int) results.stream().filter(r -> !r.created()).count();
            }
        }

        result.setCommentsCreated(totalCreated);
        result.setCommentsUpdated(totalUpdated);

        log.info("Synced {} comments from Asana ({} created, {} updated)",
                totalCreated + totalUpdated, totalCreated, totalUpdated);

        return result;
    }

    @Override
    public boolean testConnection() {
        return apiClient.testConnection();
    }

    @Override
    public void updateTaskStatus(String externalId, String status) {
        if ("completed".equalsIgnoreCase(status) || "done".equalsIgnoreCase(status)) {
            apiClient.completeTask(externalId);
        } else {
            apiClient.addTaskComment(externalId,
                    "[Signals] Task status should be updated to: " + status);
        }
    }

    @Override
    public void addComment(String entityId, String comment) {
        apiClient.addTaskComment(entityId, comment);
    }

    @Override
    public void completeTask(String externalId) {
        apiClient.completeTask(externalId);
    }
}
