package com.signalspoc.api.controller;

import com.signalspoc.api.dto.response.TaskResponse;
import com.signalspoc.domain.entity.Task;
import com.signalspoc.domain.service.TaskService;
import com.signalspoc.shared.model.Enums.ConnectorType;
import com.signalspoc.shared.model.Enums.Priority;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Task read operations")
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    @Operation(summary = "Get all tasks with optional filters")
    public ResponseEntity<Page<TaskResponse>> getTasks(
            @RequestParam(required = false) ConnectorType sourceSystem,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<Task> tasks = taskService.findWithFilters(sourceSystem, status, priority, projectId, search, pageable);
        Page<TaskResponse> response = tasks.map(TaskResponse::from);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get task by internal ID")
    public ResponseEntity<TaskResponse> getTaskById(@PathVariable Long id) {
        Task task = taskService.findById(id);
        return ResponseEntity.ok(TaskResponse.from(task));
    }

    @GetMapping("/{connector}/{externalId}")
    @Operation(summary = "Get task by connector and external ID")
    public ResponseEntity<TaskResponse> getTaskByExternalId(
            @PathVariable String connector,
            @PathVariable String externalId) {

        ConnectorType type = ConnectorType.valueOf(connector.toUpperCase());
        return taskService.findByExternalId(externalId, type)
                .map(task -> ResponseEntity.ok(TaskResponse.from(task)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "Get tasks by project ID")
    public ResponseEntity<Page<TaskResponse>> getTasksByProject(
            @PathVariable Long projectId,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<Task> tasks = taskService.findByProjectId(projectId, pageable);
        Page<TaskResponse> response = tasks.map(TaskResponse::from);
        return ResponseEntity.ok(response);
    }
}
