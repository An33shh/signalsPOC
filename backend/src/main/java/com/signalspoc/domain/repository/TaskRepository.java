package com.signalspoc.domain.repository;

import com.signalspoc.domain.entity.Task;
import com.signalspoc.shared.model.Enums.ConnectorType;
import com.signalspoc.shared.model.Enums.Priority;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    Optional<Task> findByExternalIdAndSourceSystem(String externalId, ConnectorType sourceSystem);

    List<Task> findBySourceSystem(ConnectorType sourceSystem);

    Page<Task> findBySourceSystem(ConnectorType sourceSystem, Pageable pageable);

    List<Task> findByProjectId(Long projectId);

    Page<Task> findByProjectId(Long projectId, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE " +
            "(:sourceSystem IS NULL OR t.sourceSystem = :sourceSystem) AND " +
            "(:status IS NULL OR LOWER(t.status) = LOWER(:status)) AND " +
            "(:priority IS NULL OR t.priority = :priority) AND " +
            "(:projectId IS NULL OR t.project.id = :projectId) AND " +
            "(:search IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Task> findWithFilters(
            @Param("sourceSystem") ConnectorType sourceSystem,
            @Param("status") String status,
            @Param("priority") Priority priority,
            @Param("projectId") Long projectId,
            @Param("search") String search,
            Pageable pageable);

    boolean existsByExternalIdAndSourceSystem(String externalId, ConnectorType sourceSystem);

    List<Task> findByTitleContaining(String titlePart);

    Optional<Task> findByExternalId(String externalId);
}
