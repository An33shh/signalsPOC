package com.signalspoc.domain.service;

import com.signalspoc.connector.api.ConnectorService;
import com.signalspoc.connector.model.SyncResult;
import com.signalspoc.connector.pm.api.PmConnectorService;
import com.signalspoc.domain.entity.SyncLog;
import com.signalspoc.domain.repository.SyncLogRepository;
import com.signalspoc.shared.exception.Exceptions.SyncException;
import com.signalspoc.shared.model.Enums.ConnectorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SyncOrchestratorTest {

    private PmConnectorService asanaConnector;
    private PmConnectorService linearConnector;
    private ConnectorService gitHubConnector;
    private SyncLogRepository syncLogRepository;
    private SyncOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        asanaConnector = mock(PmConnectorService.class);
        linearConnector = mock(PmConnectorService.class);
        gitHubConnector = mock(ConnectorService.class);
        syncLogRepository = mock(SyncLogRepository.class);

        when(asanaConnector.getConnectorType()).thenReturn(ConnectorType.ASANA);
        when(linearConnector.getConnectorType()).thenReturn(ConnectorType.LINEAR);
        when(gitHubConnector.getConnectorType()).thenReturn(ConnectorType.GITHUB);

        // SyncLog stub for any save
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orchestrator = new SyncOrchestrator(
                List.of(asanaConnector, linearConnector, gitHubConnector),
                List.of(asanaConnector, linearConnector),
                syncLogRepository);
        orchestrator.initConnectorMaps();
    }

    // ─── initConnectorMaps ────────────────────────────────────────────────────

    @Test
    void initConnectorMaps_registersAllConnectorsInAllMap() {
        // GitHub is in the all-connector map → testConnection works
        when(gitHubConnector.testConnection()).thenReturn(true);
        assertThat(orchestrator.testConnection(ConnectorType.GITHUB)).isTrue();
    }

    @Test
    void initConnectorMaps_registersOnlyPmConnectorsInPmMap() {
        // GITHUB sync should fail (not a PM connector)
        assertThatThrownBy(() -> orchestrator.syncAll(ConnectorType.GITHUB))
                .isInstanceOf(SyncException.class)
                .hasMessageContaining("GITHUB");
    }

    // ─── syncAll ──────────────────────────────────────────────────────────────

    @Test
    void syncAll_delegatesToPmConnectorAndReturnsSyncResult() {
        SyncResult expected = SyncResult.empty(ConnectorType.ASANA);
        expected.setTasksCreated(3);
        when(asanaConnector.syncAll()).thenReturn(expected);

        SyncResult result = orchestrator.syncAll(ConnectorType.ASANA);

        assertThat(result.getTasksCreated()).isEqualTo(3);
        verify(asanaConnector).syncAll();
    }

    @Test
    void syncAll_persistsSyncLogWithSuccessStatus() {
        when(asanaConnector.syncAll()).thenReturn(SyncResult.empty(ConnectorType.ASANA));

        orchestrator.syncAll(ConnectorType.ASANA);

        // First save creates IN_PROGRESS log, second save marks SUCCESS
        verify(syncLogRepository, times(2)).save(any(SyncLog.class));
    }

    @Test
    void syncAll_marksLogAsFailedWhenConnectorThrows() {
        when(asanaConnector.syncAll()).thenThrow(new RuntimeException("Asana API down"));

        assertThatThrownBy(() -> orchestrator.syncAll(ConnectorType.ASANA))
                .isInstanceOf(SyncException.class)
                .hasMessageContaining("ASANA");

        // Should still save the FAILED log
        verify(syncLogRepository, times(2)).save(any(SyncLog.class));
    }

    @Test
    void syncAll_throwsSyncExceptionForNonPmConnector() {
        assertThatThrownBy(() -> orchestrator.syncAll(ConnectorType.GITHUB))
                .isInstanceOf(SyncException.class)
                .hasMessageContaining("GITHUB");
    }

    // ─── syncProjects / syncTasks / syncUsers / syncComments ─────────────────

    @Test
    void syncProjects_delegatesToPmConnector() {
        SyncResult expected = SyncResult.builder().projectsCreated(2).build();
        when(asanaConnector.syncProjects()).thenReturn(expected);

        SyncResult result = orchestrator.syncProjects(ConnectorType.ASANA);
        assertThat(result.getProjectsCreated()).isEqualTo(2);
    }

    @Test
    void syncTasks_delegatesToPmConnector() {
        SyncResult expected = SyncResult.builder().tasksCreated(10).build();
        when(linearConnector.syncTasks()).thenReturn(expected);

        SyncResult result = orchestrator.syncTasks(ConnectorType.LINEAR);
        assertThat(result.getTasksCreated()).isEqualTo(10);
    }

    @Test
    void syncUsers_delegatesToPmConnector() {
        SyncResult expected = SyncResult.builder().usersCreated(5).build();
        when(asanaConnector.syncUsers()).thenReturn(expected);

        SyncResult result = orchestrator.syncUsers(ConnectorType.ASANA);
        assertThat(result.getUsersCreated()).isEqualTo(5);
    }

    @Test
    void syncComments_delegatesToPmConnector() {
        SyncResult expected = SyncResult.builder().commentsCreated(8).build();
        when(linearConnector.syncComments()).thenReturn(expected);

        SyncResult result = orchestrator.syncComments(ConnectorType.LINEAR);
        assertThat(result.getCommentsCreated()).isEqualTo(8);
    }

    @Test
    void syncAll_throwsForUnknownConnectorType() {
        // All connector types are registered; there's no "unknown" but wrong type gets same exception
        assertThatThrownBy(() -> orchestrator.syncProjects(ConnectorType.GITHUB))
                .isInstanceOf(SyncException.class);
    }

    // ─── testConnection ───────────────────────────────────────────────────────

    @Test
    void testConnection_worksForGitHub_notInPmMap() {
        when(gitHubConnector.testConnection()).thenReturn(true);
        assertThat(orchestrator.testConnection(ConnectorType.GITHUB)).isTrue();
    }

    @Test
    void testConnection_worksForPmConnectors() {
        when(asanaConnector.testConnection()).thenReturn(false);
        assertThat(orchestrator.testConnection(ConnectorType.ASANA)).isFalse();
    }

    @Test
    void testConnection_throwsForUnregisteredConnectorType() {
        // SyncOrchestrator with empty connectors
        SyncOrchestrator emptyOrchestrator = new SyncOrchestrator(List.of(), List.of(), syncLogRepository);
        emptyOrchestrator.initConnectorMaps();

        assertThatThrownBy(() -> emptyOrchestrator.testConnection(ConnectorType.ASANA))
                .isInstanceOf(SyncException.class)
                .hasMessageContaining("ASANA");
    }
}
