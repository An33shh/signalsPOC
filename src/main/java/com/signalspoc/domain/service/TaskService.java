package com.signalspoc.domain.service;

import com.signalspoc.connector.model.ConnectorTask;
import com.signalspoc.domain.entity.Project;
import com.signalspoc.domain.entity.Task;
import com.signalspoc.domain.entity.User;
import com.signalspoc.domain.repository.ProjectRepository;
import com.signalspoc.domain.repository.TaskRepository;
import com.signalspoc.domain.repository.UserRepository;
import com.signalspoc.shared.exception.Exceptions.ResourceNotFoundException;
import com.signalspoc.shared.model.Enums.ConnectorType;
import com.signalspoc.shared.model.Enums.Priority;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Task findById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    @Transactional(readOnly = true)
    public Optional<Task> findByExternalId(String externalId, ConnectorType sourceSystem) {
        return taskRepository.findByExternalIdAndSourceSystem(externalId, sourceSystem);
    }

    @Transactional(readOnly = true)
    public Page<Task> findAll(Pageable pageable) {
        return taskRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Task> findByProjectId(Long projectId, Pageable pageable) {
        return taskRepository.findByProjectId(projectId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Task> findWithFilters(ConnectorType sourceSystem, String status, Priority priority,
                                       Long projectId, String search, Pageable pageable) {
        return taskRepository.findWithFilters(sourceSystem, status, priority, projectId, search, pageable);
    }

    @Transactional
    public UserService.UpsertResult<Task> upsert(ConnectorTask connectorTask) {
        Project project = resolveProject(connectorTask.getProjectExternalId(), connectorTask.getSourceSystem());
        if (project == null) {
            log.warn("Project not found for task: {} (project: {})",
                    connectorTask.getExternalId(), connectorTask.getProjectExternalId());
            return null;
        }

        User assignee = resolveUser(connectorTask.getAssigneeExternalId(), connectorTask.getSourceSystem());

        Optional<Task> existing = taskRepository.findByExternalIdAndSourceSystem(
                connectorTask.getExternalId(),
                connectorTask.getSourceSystem());

        if (existing.isPresent()) {
            Task task = existing.get();
            updateTaskFields(task, connectorTask, project, assignee);
            Task saved = taskRepository.save(task);
            log.debug("Updated task: {} ({})", saved.getTitle(), saved.getExternalId());
            return new UserService.UpsertResult<>(saved, false);
        } else {
            Task task = createTask(connectorTask, project, assignee);
            Task saved = taskRepository.save(task);
            log.debug("Created task: {} ({})", saved.getTitle(), saved.getExternalId());
            return new UserService.UpsertResult<>(saved, true);
        }
    }

    @Transactional
    public List<UserService.UpsertResult<Task>> upsertAll(List<ConnectorTask> connectorTasks) {
        return connectorTasks.stream()
                .map(this::upsert)
                .filter(result -> result != null)
                .toList();
    }

    private Project resolveProject(String projectExternalId, ConnectorType sourceSystem) {
        if (projectExternalId == null) {
            return null;
        }
        return projectRepository.findByExternalIdAndSourceSystem(projectExternalId, sourceSystem)
                .orElse(null);
    }

    private User resolveUser(String userExternalId, ConnectorType sourceSystem) {
        if (userExternalId == null) {
            return null;
        }
        return userRepository.findByExternalIdAndSourceSystem(userExternalId, sourceSystem)
                .orElse(null);
    }

    private Task createTask(ConnectorTask connectorTask, Project project, User assignee) {
        return Task.builder()
                .externalId(connectorTask.getExternalId())
                .sourceSystem(connectorTask.getSourceSystem())
                .project(project)
                .title(connectorTask.getTitle())
                .description(connectorTask.getDescription())
                .status(connectorTask.getStatus())
                .priority(connectorTask.getPriority())
                .assignee(assignee)
                .dueDate(connectorTask.getDueDate())
                .externalCreatedAt(connectorTask.getCreatedAt())
                .externalModifiedAt(connectorTask.getModifiedAt())
                .build();
    }

    private void updateTaskFields(Task task, ConnectorTask connectorTask, Project project, User assignee) {
        task.setProject(project);
        task.setTitle(connectorTask.getTitle());
        task.setDescription(connectorTask.getDescription());
        task.setStatus(connectorTask.getStatus());
        task.setPriority(connectorTask.getPriority());
        task.setAssignee(assignee);
        task.setDueDate(connectorTask.getDueDate());
        task.setExternalModifiedAt(connectorTask.getModifiedAt());
    }
}
