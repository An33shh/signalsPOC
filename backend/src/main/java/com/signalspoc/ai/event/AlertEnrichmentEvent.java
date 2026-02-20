package com.signalspoc.ai.event;

import com.signalspoc.connector.github.dto.GitHubPullRequestDto;
import com.signalspoc.domain.entity.SyncAlert;
import com.signalspoc.domain.entity.Task;

/**
 * Published after a new SyncAlert is committed to DB.
 * AiEnrichmentWorker picks this up asynchronously and patches the alert
 * with an AI-generated suggestion and action recommendation.
 */
public record AlertEnrichmentEvent(
        Long alertId,
        SyncAlert.AlertType alertType,
        SyncAlert.AlertSeverity severity,
        GitHubPullRequestDto pr,   // nullable — context for prompt building
        Task task                  // nullable — context for prompt building
) {}
