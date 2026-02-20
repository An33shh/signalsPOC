package com.signalspoc.api.controller;

import com.signalspoc.api.dto.response.ProjectResponse;
import com.signalspoc.domain.entity.Project;
import com.signalspoc.domain.service.ProjectService;
import com.signalspoc.shared.model.Enums.ConnectorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Validated
@Tag(name = "Projects", description = "Project read operations")
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    @Operation(summary = "Get all projects with optional filters")
    public ResponseEntity<Page<ProjectResponse>> getProjects(
            @RequestParam(required = false) ConnectorType sourceSystem,
            @RequestParam(required = false) @Size(max = 100) String status,
            @RequestParam(required = false) @Size(max = 255) String search,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<Project> projects = projectService.findWithFilters(sourceSystem, status, search, pageable);
        Page<ProjectResponse> response = projects.map(ProjectResponse::from);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get project by internal ID")
    public ResponseEntity<ProjectResponse> getProjectById(@PathVariable Long id) {
        Project project = projectService.findById(id);
        return ResponseEntity.ok(ProjectResponse.from(project));
    }

    @GetMapping("/{connector}/{externalId}")
    @Operation(summary = "Get project by connector and external ID")
    public ResponseEntity<ProjectResponse> getProjectByExternalId(
            @PathVariable @Pattern(regexp = "^(asana|linear)$", flags = Pattern.Flag.CASE_INSENSITIVE) String connector,
            @PathVariable @Size(max = 255) String externalId) {

        ConnectorType type = ConnectorType.valueOf(connector.toUpperCase());
        return projectService.findByExternalId(externalId, type)
                .map(project -> ResponseEntity.ok(ProjectResponse.from(project)))
                .orElse(ResponseEntity.notFound().build());
    }
}
