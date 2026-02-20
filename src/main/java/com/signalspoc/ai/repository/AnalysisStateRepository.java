package com.signalspoc.ai.repository;

import com.signalspoc.ai.model.AnalysisState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AnalysisStateRepository extends JpaRepository<AnalysisState, Long> {

    Optional<AnalysisState> findByEntityTypeAndEntityId(String entityType, String entityId);
}
