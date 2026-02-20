package com.signalspoc.api.controller;

import com.signalspoc.connector.model.SyncResult;
import com.signalspoc.domain.entity.SyncLog;
import com.signalspoc.domain.service.SyncOrchestrator;
import com.signalspoc.shared.exception.Exceptions.SyncException;
import com.signalspoc.shared.model.Enums.ConnectorType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SyncControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean SyncOrchestrator syncOrchestrator;

    // HTTP Basic — admin/admin123 defined in SecurityConfig (InMemoryUserDetailsManager)
    private static final String AUTH = "Basic " +
            Base64.getEncoder().encodeToString("admin:admin123".getBytes());

    // ─── POST /{connector}/all ─────────────────────────────────────────────────

    @Test
    void syncAll_returnsOkForPmConnector() throws Exception {
        when(syncOrchestrator.syncAll(ConnectorType.ASANA))
                .thenReturn(SyncResult.empty(ConnectorType.ASANA));

        mockMvc.perform(post("/api/v1/sync/asana/all").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectorType").value("ASANA"));
    }

    @Test
    void syncAll_returnsBadRequestForGitHub() throws Exception {
        mockMvc.perform(post("/api/v1/sync/github/all").header("Authorization", AUTH))
                .andExpect(status().isBadRequest());
    }

    @Test
    void syncAll_returnsBadRequestForUnknownConnector() throws Exception {
        mockMvc.perform(post("/api/v1/sync/jira/all").header("Authorization", AUTH))
                .andExpect(status().isBadRequest());
    }

    @Test
    void syncAll_returnsUnauthorizedWithNoCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/sync/asana/all"))
                .andExpect(status().isUnauthorized());
    }

    // ─── POST /{connector}/projects ────────────────────────────────────────────

    @Test
    void syncProjects_returnsOkForLinear() throws Exception {
        when(syncOrchestrator.syncProjects(ConnectorType.LINEAR))
                .thenReturn(SyncResult.builder().projectsCreated(2).build());

        mockMvc.perform(post("/api/v1/sync/linear/projects").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistics.projectsCreated").value(2));
    }

    @Test
    void syncProjects_returnsBadRequestForGitHub() throws Exception {
        mockMvc.perform(post("/api/v1/sync/github/projects").header("Authorization", AUTH))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /{connector}/tasks ───────────────────────────────────────────────

    @Test
    void syncTasks_returnsOkForAsana() throws Exception {
        when(syncOrchestrator.syncTasks(ConnectorType.ASANA))
                .thenReturn(SyncResult.builder().tasksCreated(5).build());

        mockMvc.perform(post("/api/v1/sync/asana/tasks").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistics.tasksCreated").value(5));
    }

    // ─── POST /{connector}/users ───────────────────────────────────────────────

    @Test
    void syncUsers_returnsOkForLinear() throws Exception {
        when(syncOrchestrator.syncUsers(ConnectorType.LINEAR))
                .thenReturn(SyncResult.builder().usersCreated(3).build());

        mockMvc.perform(post("/api/v1/sync/linear/users").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistics.usersCreated").value(3));
    }

    // ─── POST /{connector}/comments ────────────────────────────────────────────

    @Test
    void syncComments_returnsOkForAsana() throws Exception {
        when(syncOrchestrator.syncComments(ConnectorType.ASANA))
                .thenReturn(SyncResult.builder().commentsCreated(8).build());

        mockMvc.perform(post("/api/v1/sync/asana/comments").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistics.commentsCreated").value(8));
    }

    // ─── GET /{connector}/test ─────────────────────────────────────────────────

    @Test
    void testConnection_returnsOkForGitHub() throws Exception {
        when(syncOrchestrator.testConnection(ConnectorType.GITHUB)).thenReturn(true);

        mockMvc.perform(get("/api/v1/sync/github/test").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.connector").value("GITHUB"));
    }

    @Test
    void testConnection_returnsOkForPmConnectors() throws Exception {
        when(syncOrchestrator.testConnection(ConnectorType.ASANA)).thenReturn(false);

        mockMvc.perform(get("/api/v1/sync/asana/test").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false));
    }

    @Test
    void testConnection_returnsBadRequestForUnknownConnector() throws Exception {
        mockMvc.perform(get("/api/v1/sync/unknown/test").header("Authorization", AUTH))
                .andExpect(status().isBadRequest());
    }

    // ─── GET /logs ─────────────────────────────────────────────────────────────

    @Test
    void getSyncLogs_returnsPagedResults() throws Exception {
        Page<SyncLog> emptyPage = new PageImpl<>(List.of());
        when(syncOrchestrator.getSyncLogs(isNull(), any(Pageable.class))).thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/sync/logs").header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getSyncLogs_acceptsConnectorTypeParam() throws Exception {
        Page<SyncLog> emptyPage = new PageImpl<>(List.of());
        when(syncOrchestrator.getSyncLogs(eq(ConnectorType.ASANA), any(Pageable.class))).thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/sync/logs").param("connectorType", "ASANA")
                .header("Authorization", AUTH))
                .andExpect(status().isOk());
    }

    // ─── GET /logs/{id} ────────────────────────────────────────────────────────

    @Test
    void getSyncLog_returnsNotFoundForMissingId() throws Exception {
        when(syncOrchestrator.getSyncLog(999L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/sync/logs/999").header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }

    // ─── SyncException propagation ─────────────────────────────────────────────

    @Test
    void syncAll_returns500WhenOrchestratorThrowsSyncException() throws Exception {
        when(syncOrchestrator.syncAll(ConnectorType.ASANA))
                .thenThrow(new SyncException("Connector unavailable"));

        mockMvc.perform(post("/api/v1/sync/asana/all").header("Authorization", AUTH))
                .andExpect(status().isInternalServerError());
    }
}
