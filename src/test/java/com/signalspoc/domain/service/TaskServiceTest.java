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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock ProjectRepository projectRepository;
    @Mock UserRepository userRepository;

    @InjectMocks TaskService taskService;

    // ─── findById ─────────────────────────────────────────────────────────────

    @Test
    void findById_returnsTaskWhenFound() {
        Task task = Task.builder().id(1L).title("Test Task").build();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThat(taskService.findById(1L)).isSameAs(task);
    }

    @Test
    void findById_throwsResourceNotFoundExceptionForMissingId() {
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ─── upsert: create path ──────────────────────────────────────────────────

    @Test
    void upsert_createsNewTaskWhenNotExists() {
        ConnectorTask ct = connectorTask("ext-1", "proj-1", "user-1");
        Project project = Project.builder().id(1L).externalId("proj-1").build();
        User user = User.builder().id(2L).externalId("user-1").build();
        Task saved = Task.builder().id(10L).externalId("ext-1").build();

        when(projectRepository.findByExternalIdAndSourceSystem("proj-1", ConnectorType.ASANA))
                .thenReturn(Optional.of(project));
        when(userRepository.findByExternalIdAndSourceSystem("user-1", ConnectorType.ASANA))
                .thenReturn(Optional.of(user));
        when(taskRepository.findByExternalIdAndSourceSystem("ext-1", ConnectorType.ASANA))
                .thenReturn(Optional.empty());
        when(taskRepository.save(any())).thenReturn(saved);

        var result = taskService.upsert(ct);

        assertThat(result).isNotNull();
        assertThat(result.created()).isTrue();
        assertThat(result.entity()).isSameAs(saved);
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void upsert_setsAssigneeToNullWhenAssigneeNotFound() {
        ConnectorTask ct = connectorTask("ext-1", "proj-1", "missing-user");
        Project project = Project.builder().id(1L).build();
        Task saved = Task.builder().id(10L).build();

        when(projectRepository.findByExternalIdAndSourceSystem("proj-1", ConnectorType.ASANA))
                .thenReturn(Optional.of(project));
        when(userRepository.findByExternalIdAndSourceSystem("missing-user", ConnectorType.ASANA))
                .thenReturn(Optional.empty());
        when(taskRepository.findByExternalIdAndSourceSystem("ext-1", ConnectorType.ASANA))
                .thenReturn(Optional.empty());
        when(taskRepository.save(any())).thenReturn(saved);

        var result = taskService.upsert(ct);

        assertThat(result).isNotNull();
    }

    // ─── upsert: update path ──────────────────────────────────────────────────

    @Test
    void upsert_updatesExistingTaskInPlace() {
        ConnectorTask ct = connectorTask("ext-1", "proj-1", null);
        Project project = Project.builder().id(1L).externalId("proj-1").build();
        Task existing = Task.builder().id(5L).externalId("ext-1").title("Old Title").build();
        Task saved = Task.builder().id(5L).externalId("ext-1").title("New Title").build();

        when(projectRepository.findByExternalIdAndSourceSystem("proj-1", ConnectorType.ASANA))
                .thenReturn(Optional.of(project));
        when(taskRepository.findByExternalIdAndSourceSystem("ext-1", ConnectorType.ASANA))
                .thenReturn(Optional.of(existing));
        when(taskRepository.save(existing)).thenReturn(saved);

        var result = taskService.upsert(ct);

        assertThat(result).isNotNull();
        assertThat(result.created()).isFalse();
        // Title is updated on the existing entity before save
        assertThat(existing.getTitle()).isEqualTo("New Title");
        verify(taskRepository).save(existing);
    }

    // ─── upsert: null-project guard ───────────────────────────────────────────

    @Test
    void upsert_returnsNullWhenProjectExternalIdIsNull() {
        ConnectorTask ct = connectorTask("ext-1", null, null);

        var result = taskService.upsert(ct);

        assertThat(result).isNull();
        verify(projectRepository, never()).findByExternalIdAndSourceSystem(any(), any());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void upsert_returnsNullWhenProjectNotFoundInDb() {
        ConnectorTask ct = connectorTask("ext-1", "missing-proj", null);
        when(projectRepository.findByExternalIdAndSourceSystem("missing-proj", ConnectorType.ASANA))
                .thenReturn(Optional.empty());

        var result = taskService.upsert(ct);

        assertThat(result).isNull();
        verify(taskRepository, never()).save(any());
    }

    // ─── upsertAll ────────────────────────────────────────────────────────────

    @Test
    void upsertAll_filtersNullResultsFromMissingProjects() {
        ConnectorTask withProject = connectorTask("ext-1", "proj-1", null);
        ConnectorTask withoutProject = connectorTask("ext-2", null, null);

        Project project = Project.builder().id(1L).build();
        Task saved = Task.builder().id(10L).build();

        when(projectRepository.findByExternalIdAndSourceSystem("proj-1", ConnectorType.ASANA))
                .thenReturn(Optional.of(project));
        when(taskRepository.findByExternalIdAndSourceSystem("ext-1", ConnectorType.ASANA))
                .thenReturn(Optional.empty());
        when(taskRepository.save(any())).thenReturn(saved);

        List<UserService.UpsertResult<Task>> results =
                taskService.upsertAll(List.of(withProject, withoutProject));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).created()).isTrue();
    }

    @Test
    void upsertAll_returnsEmptyListWhenAllProjectsMissing() {
        ConnectorTask ct1 = connectorTask("ext-1", null, null);
        ConnectorTask ct2 = connectorTask("ext-2", null, null);

        List<UserService.UpsertResult<Task>> results = taskService.upsertAll(List.of(ct1, ct2));

        assertThat(results).isEmpty();
        verify(taskRepository, never()).save(any());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private ConnectorTask connectorTask(String externalId, String projectId, String assigneeId) {
        return ConnectorTask.builder()
                .externalId(externalId)
                .sourceSystem(ConnectorType.ASANA)
                .projectExternalId(projectId)
                .assigneeExternalId(assigneeId)
                .title("New Title")
                .status("todo")
                .build();
    }
}
