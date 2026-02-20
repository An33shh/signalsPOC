package com.signalspoc.domain.service;

import com.signalspoc.connector.api.ConnectorService;
import com.signalspoc.connector.model.SyncResult;
import com.signalspoc.connector.pm.api.PmConnectorService;
import com.signalspoc.domain.entity.SyncLog;
import com.signalspoc.domain.repository.SyncLogRepository;
import com.signalspoc.shared.exception.Exceptions.SyncException;
import com.signalspoc.shared.model.Enums.ConnectorType;
import com.signalspoc.shared.model.Enums.SyncStatus;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncOrchestrator {

    private final List<ConnectorService> allConnectorServices;
    private final List<PmConnectorService> pmConnectorServices;
    private final SyncLogRepository syncLogRepository;

    private Map<ConnectorType, ConnectorService> connectorMap;
    private Map<ConnectorType, PmConnectorService> pmConnectorMap;

    @PostConstruct
    void initConnectorMaps() {
        this.connectorMap = allConnectorServices.stream()
                .collect(Collectors.toMap(ConnectorService::getConnectorType, Function.identity()));
        this.pmConnectorMap = pmConnectorServices.stream()
                .collect(Collectors.toMap(PmConnectorService::getConnectorType, Function.identity()));
        log.info("Initialized {} connectors: {}", connectorMap.size(), connectorMap.keySet());
        log.info("Initialized {} PM connectors: {}", pmConnectorMap.size(), pmConnectorMap.keySet());
    }

    private PmConnectorService getPmConnector(ConnectorType type) {
        PmConnectorService connector = pmConnectorMap.get(type);
        if (connector == null) {
            throw new SyncException("PM connector not found for type: " + type +
                    ". Valid PM connector types: ASANA, LINEAR.");
        }
        return connector;
    }

    public ConnectorService getConnector(ConnectorType type) {
        ConnectorService connector = connectorMap.get(type);
        if (connector == null) {
            throw new SyncException("Connector not found for type: " + type);
        }
        return connector;
    }

    @Transactional
    public SyncResult syncAll(ConnectorType connectorType) {
        log.info("Starting full sync for connector: {}", connectorType);

        SyncLog syncLog = createSyncLog(connectorType);

        try {
            PmConnectorService connector = getPmConnector(connectorType);
            SyncResult result = connector.syncAll();

            completeSyncLog(syncLog, result);
            log.info("Completed full sync for connector: {} - Projects: {}, Tasks: {}, Users: {}, Comments: {}",
                    connectorType,
                    result.getProjectsCreated() + result.getProjectsUpdated(),
                    result.getTasksCreated() + result.getTasksUpdated(),
                    result.getUsersCreated() + result.getUsersUpdated(),
                    result.getCommentsCreated() + result.getCommentsUpdated());

            return result;

        } catch (Exception e) {
            log.error("Sync failed for connector: {}", connectorType, e);
            failSyncLog(syncLog, e.getMessage());
            throw new SyncException("Sync failed for " + connectorType, e);
        }
    }

    @Transactional
    public SyncResult syncProjects(ConnectorType connectorType) {
        log.info("Starting project sync for connector: {}", connectorType);
        return getPmConnector(connectorType).syncProjects();
    }

    @Transactional
    public SyncResult syncTasks(ConnectorType connectorType) {
        log.info("Starting task sync for connector: {}", connectorType);
        return getPmConnector(connectorType).syncTasks();
    }

    @Transactional
    public SyncResult syncUsers(ConnectorType connectorType) {
        log.info("Starting user sync for connector: {}", connectorType);
        return getPmConnector(connectorType).syncUsers();
    }

    @Transactional
    public SyncResult syncComments(ConnectorType connectorType) {
        log.info("Starting comment sync for connector: {}", connectorType);
        return getPmConnector(connectorType).syncComments();
    }

    public boolean testConnection(ConnectorType connectorType) {
        return getConnector(connectorType).testConnection();
    }

    @Transactional(readOnly = true)
    public Page<SyncLog> getSyncLogs(ConnectorType connectorType, Pageable pageable) {
        if (connectorType != null) {
            return syncLogRepository.findByConnectorTypeOrderByStartTimeDesc(connectorType, pageable);
        }
        return syncLogRepository.findAllByOrderByStartTimeDesc(pageable);
    }

    @Transactional(readOnly = true)
    public SyncLog getSyncLog(Long id) {
        return syncLogRepository.findById(id).orElse(null);
    }

    private SyncLog createSyncLog(ConnectorType connectorType) {
        SyncLog syncLog = SyncLog.builder()
                .connectorType(connectorType)
                .status(SyncStatus.IN_PROGRESS)
                .startTime(LocalDateTime.now())
                .build();
        return syncLogRepository.save(syncLog);
    }

    private void completeSyncLog(SyncLog syncLog, SyncResult result) {
        syncLog.setStatus(SyncStatus.SUCCESS);
        syncLog.setEndTime(LocalDateTime.now());
        syncLog.setProjectsSynced(result.getProjectsCreated() + result.getProjectsUpdated());
        syncLog.setTasksSynced(result.getTasksCreated() + result.getTasksUpdated());
        syncLog.setUsersSynced(result.getUsersCreated() + result.getUsersUpdated());
        syncLog.setCommentsSynced(result.getCommentsCreated() + result.getCommentsUpdated());
        syncLogRepository.save(syncLog);
    }

    private void failSyncLog(SyncLog syncLog, String errorMessage) {
        syncLog.setStatus(SyncStatus.FAILED);
        syncLog.setEndTime(LocalDateTime.now());
        syncLog.setErrorMessage(errorMessage);
        syncLogRepository.save(syncLog);
    }
}
