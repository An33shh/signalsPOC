package com.signalspoc.connector.pm.api;

import com.signalspoc.connector.api.ConnectorService;
import com.signalspoc.connector.model.SyncResult;

/**
 * Interface for PM tool connectors (Asana, Linear, and future tools like Jira).
 * Extends the base identity/test interface with full sync and write-back capabilities.
 */
public interface PmConnectorService extends ConnectorService {

    SyncResult syncAll();

    SyncResult syncProjects();

    SyncResult syncTasks();

    SyncResult syncUsers();

    SyncResult syncComments();

    void updateTaskStatus(String externalId, String status);

    void addComment(String entityId, String comment);

    void completeTask(String externalId);
}
