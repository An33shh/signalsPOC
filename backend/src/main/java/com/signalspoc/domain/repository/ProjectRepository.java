package com.signalspoc.domain.repository;

import com.signalspoc.domain.entity.Project;
import com.signalspoc.shared.model.Enums.ConnectorType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByExternalIdAndSourceSystem(String externalId, ConnectorType sourceSystem);

    List<Project> findBySourceSystem(ConnectorType sourceSystem);

    Page<Project> findBySourceSystem(ConnectorType sourceSystem, Pageable pageable);

    @Query("SELECT p FROM Project p WHERE " +
            "(:sourceSystem IS NULL OR p.sourceSystem = :sourceSystem) AND " +
            "(:status IS NULL OR p.status = :status) AND " +
            "(:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Project> findWithFilters(
            @Param("sourceSystem") ConnectorType sourceSystem,
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable);

    boolean existsByExternalIdAndSourceSystem(String externalId, ConnectorType sourceSystem);
}
