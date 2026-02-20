package com.signalspoc.api.controller;

import com.signalspoc.api.dto.response.SyncLogResponse;
import com.signalspoc.api.dto.response.SyncResponse;
import com.signalspoc.connector.model.SyncResult;
import com.signalspoc.domain.entity.SyncLog;
import com.signalspoc.domain.service.SyncOrchestrator;
import com.signalspoc.shared.model.Enums.ConnectorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Sync", description = "Data synchronization operations")
public class SyncController {

    private final SyncOrchestrator syncOrchestrator;

    @PostMapping("/{connector}/all")
    @Operation(summary = "Sync all data from a connector")
    public ResponseEntity<SyncResponse> syncAll(@PathVariable String connector) {
        ConnectorType type = parsePmConnectorType(connector);
        SyncResult result = syncOrchestrator.syncAll(type);
        return ResponseEntity.ok(SyncResponse.from(result));
    }

    @PostMapping("/{connector}/projects")
    @Operation(summary = "Sync projects from a connector")
    public ResponseEntity<SyncResponse> syncProjects(@PathVariable String connector) {
        ConnectorType type = parsePmConnectorType(connector);
        SyncResult result = syncOrchestrator.syncProjects(type);
        return ResponseEntity.ok(SyncResponse.from(result));
    }

    @PostMapping("/{connector}/tasks")
    @Operation(summary = "Sync tasks from a connector")
    public ResponseEntity<SyncResponse> syncTasks(@PathVariable String connector) {
        ConnectorType type = parsePmConnectorType(connector);
        SyncResult result = syncOrchestrator.syncTasks(type);
        return ResponseEntity.ok(SyncResponse.from(result));
    }

    @PostMapping("/{connector}/users")
    @Operation(summary = "Sync users from a connector")
    public ResponseEntity<SyncResponse> syncUsers(@PathVariable String connector) {
        ConnectorType type = parsePmConnectorType(connector);
        SyncResult result = syncOrchestrator.syncUsers(type);
        return ResponseEntity.ok(SyncResponse.from(result));
    }

    @PostMapping("/{connector}/comments")
    @Operation(summary = "Sync comments from a connector")
    public ResponseEntity<SyncResponse> syncComments(@PathVariable String connector) {
        ConnectorType type = parsePmConnectorType(connector);
        SyncResult result = syncOrchestrator.syncComments(type);
        return ResponseEntity.ok(SyncResponse.from(result));
    }

    @GetMapping("/{connector}/test")
    @Operation(summary = "Test connection to a connector")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable String connector) {
        ConnectorType type = parseConnectorType(connector);
        boolean success = syncOrchestrator.testConnection(type);
        return ResponseEntity.ok(Map.of(
                "connector", type,
                "connected", success,
                "message", success ? "Connection successful" : "Connection failed"
        ));
    }

    @GetMapping("/logs")
    @Operation(summary = "Get sync logs")
    public ResponseEntity<Page<SyncLogResponse>> getSyncLogs(
            @RequestParam(required = false) ConnectorType connectorType,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<SyncLog> logs = syncOrchestrator.getSyncLogs(connectorType, pageable);
        Page<SyncLogResponse> response = logs.map(SyncLogResponse::from);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/logs/{id}")
    @Operation(summary = "Get sync log by ID")
    public ResponseEntity<SyncLogResponse> getSyncLog(@PathVariable Long id) {
        SyncLog log = syncOrchestrator.getSyncLog(id);
        if (log == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(SyncLogResponse.from(log));
    }

    private static final Set<ConnectorType> PM_CONNECTOR_TYPES = Set.of(
            ConnectorType.ASANA, ConnectorType.LINEAR);

    private ConnectorType parseConnectorType(String connector) {
        try {
            return ConnectorType.valueOf(connector.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown connector type: " + connector);
        }
    }

    private ConnectorType parsePmConnectorType(String connector) {
        ConnectorType type = parseConnectorType(connector);
        if (!PM_CONNECTOR_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "'" + type + "' is not a PM connector and has no sync operations. " +
                    "Valid PM connector types: ASANA, LINEAR. Use /" + connector.toLowerCase() + "/test for connectivity.");
        }
        return type;
    }
}
