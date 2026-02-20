package com.signalspoc.domain.repository;

import com.signalspoc.domain.entity.SyncLog;
import com.signalspoc.shared.model.Enums.ConnectorType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    Page<SyncLog> findByConnectorTypeOrderByStartTimeDesc(ConnectorType connectorType, Pageable pageable);

    Page<SyncLog> findAllByOrderByStartTimeDesc(Pageable pageable);

    Optional<SyncLog> findTopByConnectorTypeOrderByStartTimeDesc(ConnectorType connectorType);
}
