package com.signalspoc.connector.github;

import com.signalspoc.connector.api.ConnectorService;
import com.signalspoc.shared.model.Enums.ConnectorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * GitHub is a source control (SVC) tool, not a project management system.
 * This connector handles connection testing only — PM data (projects, tasks,
 * users, comments) is NOT synced from GitHub.
 *
 * GitHub's role in this system:
 *  - PR monitoring: SyncDiscrepancyDetector uses GitHubApiClient directly
 *  - Write-back:    AlertActionExecutor uses GitHubApiClient for PR comments/labels/approvals
 */
@Service
@ConditionalOnProperty(name = "connectors.github.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class GitHubConnectorService implements ConnectorService {

    private final GitHubApiClient apiClient;

    @Override
    public ConnectorType getConnectorType() {
        return ConnectorType.GITHUB;
    }

    @Override
    public boolean testConnection() {
        return apiClient.testConnection();
    }
}
