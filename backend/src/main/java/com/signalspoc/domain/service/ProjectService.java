package com.signalspoc.domain.service;

import com.signalspoc.connector.model.ConnectorProject;
import com.signalspoc.domain.entity.Project;
import com.signalspoc.domain.entity.User;
import com.signalspoc.domain.repository.ProjectRepository;
import com.signalspoc.domain.repository.UserRepository;
import com.signalspoc.shared.exception.Exceptions.ResourceNotFoundException;
import com.signalspoc.shared.model.Enums.ConnectorType;
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
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Project findById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project", id));
    }

    @Transactional(readOnly = true)
    public Optional<Project> findByExternalId(String externalId, ConnectorType sourceSystem) {
        return projectRepository.findByExternalIdAndSourceSystem(externalId, sourceSystem);
    }

    @Transactional(readOnly = true)
    public Page<Project> findAll(Pageable pageable) {
        return projectRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Project> findWithFilters(ConnectorType sourceSystem, String status, String search, Pageable pageable) {
        return projectRepository.findWithFilters(sourceSystem, status, search, pageable);
    }

    @Transactional
    public UserService.UpsertResult<Project> upsert(ConnectorProject connectorProject) {
        Optional<Project> existing = projectRepository.findByExternalIdAndSourceSystem(
                connectorProject.getExternalId(),
                connectorProject.getSourceSystem());

        User owner = resolveOwner(connectorProject.getOwnerExternalId(), connectorProject.getSourceSystem());

        if (existing.isPresent()) {
            Project project = existing.get();
            updateProjectFields(project, connectorProject, owner);
            Project saved = projectRepository.save(project);
            log.debug("Updated project: {} ({})", saved.getName(), saved.getExternalId());
            return new UserService.UpsertResult<>(saved, false);
        } else {
            Project project = createProject(connectorProject, owner);
            Project saved = projectRepository.save(project);
            log.debug("Created project: {} ({})", saved.getName(), saved.getExternalId());
            return new UserService.UpsertResult<>(saved, true);
        }
    }

    @Transactional
    public List<UserService.UpsertResult<Project>> upsertAll(List<ConnectorProject> connectorProjects) {
        return connectorProjects.stream()
                .map(this::upsert)
                .toList();
    }

    private User resolveOwner(String ownerExternalId, ConnectorType sourceSystem) {
        if (ownerExternalId == null) {
            return null;
        }
        return userRepository.findByExternalIdAndSourceSystem(ownerExternalId, sourceSystem)
                .orElse(null);
    }

    private Project createProject(ConnectorProject connectorProject, User owner) {
        return Project.builder()
                .externalId(connectorProject.getExternalId())
                .sourceSystem(connectorProject.getSourceSystem())
                .name(connectorProject.getName())
                .description(connectorProject.getDescription())
                .status(connectorProject.getStatus())
                .owner(owner)
                .externalCreatedAt(connectorProject.getCreatedAt())
                .externalModifiedAt(connectorProject.getModifiedAt())
                .build();
    }

    private void updateProjectFields(Project project, ConnectorProject connectorProject, User owner) {
        project.setName(connectorProject.getName());
        project.setDescription(connectorProject.getDescription());
        project.setStatus(connectorProject.getStatus());
        project.setOwner(owner);
        project.setExternalModifiedAt(connectorProject.getModifiedAt());
    }
}
