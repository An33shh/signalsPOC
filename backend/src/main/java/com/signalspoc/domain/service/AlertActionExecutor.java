package com.signalspoc.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signalspoc.ai.model.AiActionRecommendation;
import com.signalspoc.connector.pm.api.PmConnectorService;
import com.signalspoc.domain.entity.SyncAlert;
import com.signalspoc.domain.repository.SyncAlertRepository;
import com.signalspoc.connector.github.GitHubApiClient;
import com.signalspoc.connector.github.GitHubConfig;
import com.signalspoc.shared.exception.Exceptions.ResourceNotFoundException;
import com.signalspoc.shared.model.Enums.ConnectorType;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AlertActionExecutor {

    private final SyncAlertRepository alertRepository;
    private final List<PmConnectorService> pmConnectorServices;
    private final GitHubApiClient gitHubApiClient;
    private final GitHubConfig gitHubConfig;
    private final ObjectMapper objectMapper;

    private Map<ConnectorType, PmConnectorService> pmConnectorMap;

    public AlertActionExecutor(SyncAlertRepository alertRepository,
                               @Autowired(required = false) List<PmConnectorService> pmConnectorServices,
                               @Autowired(required = false) GitHubApiClient gitHubApiClient,
                               @Autowired(required = false) GitHubConfig gitHubConfig,
                               ObjectMapper objectMapper) {
        this.alertRepository = alertRepository;
        this.pmConnectorServices = pmConnectorServices != null ? pmConnectorServices : List.of();
        this.gitHubApiClient = gitHubApiClient;
        this.gitHubConfig = gitHubConfig;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void initPmConnectorMap() {
        this.pmConnectorMap = pmConnectorServices.stream()
                .collect(Collectors.toMap(PmConnectorService::getConnectorType, Function.identity()));
    }

    @Data
    @Builder
    public static class ActionResult {
        private boolean success;
        private String description;
        private String actionTaken;
        private String aiReasoning;
    }

    @Transactional
    public ActionResult executeAction(Long alertId) {
        SyncAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + alertId));

        if (Boolean.TRUE.equals(alert.getIsResolved())) {
            return ActionResult.builder()
                    .success(false)
                    .description("Alert is already resolved")
                    .build();
        }

        try {
            ActionResult result = dispatchAction(alert);

            if (result.isSuccess()) {
                alert.setIsResolved(true);
                alert.setResolvedAt(LocalDateTime.now());
                alert.setIsRead(true);
                alertRepository.save(alert);

                if (!"NO_ACTION".equals(result.getActionTaken())) {
                    addAuditComments(alert, result);
                }
            }

            return result;
        } catch (Exception e) {
            log.error("Error executing action for alert {}", alertId, e);
            return ActionResult.builder()
                    .success(false)
                    .description("Action failed: " + e.getMessage())
                    .build();
        }
    }

    private ActionResult dispatchAction(SyncAlert alert) {
        if (alert.getAiActionJson() != null) {
            try {
                AiActionRecommendation rec = objectMapper.readValue(
                        alert.getAiActionJson(), AiActionRecommendation.class);
                return executeAiRecommendation(alert, rec);
            } catch (Exception e) {
                log.warn("Failed to parse AI action JSON for alert {}, falling back to legacy dispatch", alert.getId(), e);
            }
        }
        return legacyDispatch(alert);
    }

    private ActionResult executeAiRecommendation(SyncAlert alert, AiActionRecommendation rec) {
        if (rec.getActionType() == null) {
            return ActionResult.builder()
                    .success(false)
                    .description("AI recommendation has no action type")
                    .aiReasoning(rec.getReasoning())
                    .build();
        }

        return switch (rec.getActionType()) {
            case COMPLETE_TASK -> executeCompleteTask(alert, rec);
            case UPDATE_TASK_STATUS -> executeUpdateTaskStatus(alert, rec);
            case ADD_COMMENT -> executeAddComment(alert, rec);
            case ADD_PR_COMMENT -> executeAddPRComment(alert, rec);
            case UPDATE_PR_LABELS -> executeUpdatePRLabels(alert, rec);
            case APPROVE_PR -> executeApprovePR(alert, rec);
            case NO_ACTION -> ActionResult.builder()
                    .success(true)
                    .actionTaken("NO_ACTION")
                    .description("AI determined no action is needed")
                    .aiReasoning(rec.getReasoning())
                    .build();
            case MANUAL_REVIEW -> ActionResult.builder()
                    .success(false)
                    .actionTaken("MANUAL_REVIEW")
                    .description("AI recommends manual review: " + rec.getReasoning())
                    .aiReasoning(rec.getReasoning())
                    .build();
        };
    }

    private ActionResult executeCompleteTask(SyncAlert alert, AiActionRecommendation rec) {
        ConnectorType platform = rec.getTargetPlatform() != null ? rec.getTargetPlatform() : alert.getTargetSystem();
        String entityId = rec.getTargetEntityId() != null ? rec.getTargetEntityId() : alert.getTargetId();

        PmConnectorService connector = pmConnectorMap.get(platform);
        if (connector != null) {
            connector.completeTask(entityId);
            return ActionResult.builder()
                    .success(true)
                    .actionTaken("COMPLETE_TASK")
                    .description("Marked " + platform + " task as complete")
                    .aiReasoning(rec.getReasoning())
                    .build();
        }

        return ActionResult.builder()
                .success(false)
                .description("Target PM connector not available: " + platform)
                .aiReasoning(rec.getReasoning())
                .build();
    }

    private ActionResult executeUpdateTaskStatus(SyncAlert alert, AiActionRecommendation rec) {
        ConnectorType platform = rec.getTargetPlatform() != null ? rec.getTargetPlatform() : alert.getTargetSystem();
        String entityId = rec.getTargetEntityId() != null ? rec.getTargetEntityId() : alert.getTargetId();
        String status = rec.getParameters() != null ? rec.getParameters().get("status") : "In Review";
        String prComment = rec.getParameters() != null ? rec.getParameters().get("comment") : null;

        PmConnectorService connector = pmConnectorMap.get(platform);
        if (connector != null) {
            connector.updateTaskStatus(entityId, status);
            if (prComment != null) {
                connector.addComment(entityId, "[Signals] " + prComment);
            }
            return ActionResult.builder()
                    .success(true)
                    .actionTaken("UPDATE_TASK_STATUS")
                    .description("Updated " + platform + " task status to '" + status + "'")
                    .aiReasoning(rec.getReasoning())
                    .build();
        }

        return ActionResult.builder()
                .success(false)
                .description("Target PM connector not available: " + platform)
                .aiReasoning(rec.getReasoning())
                .build();
    }

    private ActionResult executeApprovePR(SyncAlert alert, AiActionRecommendation rec) {
        if (gitHubApiClient == null) {
            return ActionResult.builder()
                    .success(false)
                    .description("GitHub connector not available")
                    .aiReasoning(rec.getReasoning())
                    .build();
        }

        String body = rec.getParameters() != null
                ? rec.getParameters().getOrDefault("body", "[Signals] Approved by AI assistant")
                : "[Signals] Approved by AI assistant";

        PRInfo prInfo = parsePRUrl(alert.getSourceUrl());
        if (prInfo != null) {
            gitHubApiClient.approvePR(prInfo.owner, prInfo.repo, prInfo.number, body);
            return ActionResult.builder()
                    .success(true)
                    .actionTaken("APPROVE_PR")
                    .description("Submitted APPROVED review on GitHub PR #" + prInfo.number)
                    .aiReasoning(rec.getReasoning())
                    .build();
        }

        return ActionResult.builder()
                .success(false)
                .description("Could not parse PR URL from alert")
                .aiReasoning(rec.getReasoning())
                .build();
    }

    private ActionResult executeAddComment(SyncAlert alert, AiActionRecommendation rec) {
        ConnectorType platform = rec.getTargetPlatform() != null ? rec.getTargetPlatform() : alert.getTargetSystem();
        String entityId = rec.getTargetEntityId() != null ? rec.getTargetEntityId() : alert.getTargetId();
        String comment = rec.getParameters() != null ? rec.getParameters().get("comment") : "Action required — please review.";

        PmConnectorService connector = pmConnectorMap.get(platform);
        if (connector != null) {
            connector.addComment(entityId, "[Signals] " + comment);
            return ActionResult.builder()
                    .success(true)
                    .actionTaken("ADD_COMMENT")
                    .description("Added comment to " + platform + " task")
                    .aiReasoning(rec.getReasoning())
                    .build();
        }

        return ActionResult.builder()
                .success(false)
                .description("Target PM connector not available: " + platform)
                .aiReasoning(rec.getReasoning())
                .build();
    }

    private ActionResult executeAddPRComment(SyncAlert alert, AiActionRecommendation rec) {
        if (gitHubApiClient == null) {
            return ActionResult.builder()
                    .success(false)
                    .description("GitHub connector not available")
                    .aiReasoning(rec.getReasoning())
                    .build();
        }

        String comment = rec.getParameters() != null ? rec.getParameters().get("comment") : "Action required — please review this PR.";
        PRInfo prInfo = parsePRUrl(alert.getSourceUrl());
        if (prInfo != null) {
            gitHubApiClient.addPRComment(prInfo.owner, prInfo.repo, prInfo.number, "[Signals] " + comment);
            return ActionResult.builder()
                    .success(true)
                    .actionTaken("ADD_PR_COMMENT")
                    .description("Added comment to GitHub PR")
                    .aiReasoning(rec.getReasoning())
                    .build();
        }

        return ActionResult.builder()
                .success(false)
                .description("Could not parse PR URL from alert")
                .aiReasoning(rec.getReasoning())
                .build();
    }

    private ActionResult executeUpdatePRLabels(SyncAlert alert, AiActionRecommendation rec) {
        if (gitHubApiClient == null) {
            return ActionResult.builder()
                    .success(false)
                    .description("GitHub connector not available")
                    .aiReasoning(rec.getReasoning())
                    .build();
        }

        String labelsStr = rec.getParameters() != null ? rec.getParameters().get("labels") : "";
        List<String> labels = Arrays.stream(labelsStr.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).toList();

        PRInfo prInfo = parsePRUrl(alert.getSourceUrl());
        if (prInfo != null) {
            gitHubApiClient.updatePRLabels(prInfo.owner, prInfo.repo, prInfo.number, labels);
            return ActionResult.builder()
                    .success(true)
                    .actionTaken("UPDATE_PR_LABELS")
                    .description("Updated labels on GitHub PR: " + labels)
                    .aiReasoning(rec.getReasoning())
                    .build();
        }

        return ActionResult.builder()
                .success(false)
                .description("Could not parse PR URL from alert")
                .aiReasoning(rec.getReasoning())
                .build();
    }

    private ActionResult legacyDispatch(SyncAlert alert) {
        return switch (alert.getAlertType()) {
            case PR_MERGED_TASK_OPEN -> handlePRMergedTaskOpen(alert);
            case PR_READY_TASK_NOT_UPDATED -> handlePRReadyTaskNotUpdated(alert);
            case STALE_PR -> handleStalePR(alert);
            case MISSING_LINK -> ActionResult.builder()
                    .success(false)
                    .description("Missing link alerts require manual action — add a task reference to the PR")
                    .build();
            default -> ActionResult.builder()
                    .success(false)
                    .description("No automatic action available for alert type: " + alert.getAlertType())
                    .build();
        };
    }

    private ActionResult handlePRMergedTaskOpen(SyncAlert alert) {
        PmConnectorService connector = pmConnectorMap.get(alert.getTargetSystem());
        if (connector != null) {
            connector.completeTask(alert.getTargetId());
            return ActionResult.builder()
                    .success(true)
                    .actionTaken("COMPLETE_TASK")
                    .description("Marked " + alert.getTargetSystem() + " task as complete")
                    .build();
        }

        return ActionResult.builder()
                .success(false)
                .description("Target PM connector not available: " + alert.getTargetSystem())
                .build();
    }

    private ActionResult handlePRReadyTaskNotUpdated(SyncAlert alert) {
        PmConnectorService connector = pmConnectorMap.get(alert.getTargetSystem());
        if (connector != null) {
            connector.updateTaskStatus(alert.getTargetId(), "In Review");
            connector.addComment(alert.getTargetId(),
                    "[Signals] PR is ready for review — task status should be updated to 'In Review'");
            return ActionResult.builder()
                    .success(true)
                    .actionTaken("UPDATE_STATUS")
                    .description("Updated " + alert.getTargetSystem() + " task status to 'In Review'")
                    .build();
        }

        return ActionResult.builder()
                .success(false)
                .description("Target PM connector not available: " + alert.getTargetSystem())
                .build();
    }

    private ActionResult handleStalePR(SyncAlert alert) {
        if (gitHubApiClient == null) {
            return ActionResult.builder()
                    .success(false)
                    .description("GitHub connector is not enabled — cannot comment on stale PR")
                    .build();
        }
        if (alert.getSourceUrl() == null) {
            return ActionResult.builder()
                    .success(false)
                    .description("Alert has no source URL — cannot locate PR")
                    .build();
        }
        PRInfo prInfo = parsePRUrl(alert.getSourceUrl());
        if (prInfo == null) {
            return ActionResult.builder()
                    .success(false)
                    .description("Could not parse PR URL: " + alert.getSourceUrl())
                    .build();
        }
        gitHubApiClient.addPRComment(prInfo.owner, prInfo.repo, prInfo.number,
                "[Signals] This PR has been flagged as stale (open > 7 days). Please review or close if no longer needed.");
        return ActionResult.builder()
                .success(true)
                .actionTaken("ADD_PR_COMMENT")
                .description("Added stale PR reminder comment to GitHub")
                .build();
    }

    private void addAuditComments(SyncAlert alert, ActionResult result) {
        try {
            String auditMessage = String.format("[Signals] Auto-action executed: %s", result.getDescription());
            PmConnectorService connector = pmConnectorMap.get(alert.getTargetSystem());
            if (connector != null && alert.getTargetId() != null) {
                connector.addComment(alert.getTargetId(), auditMessage);
            }
        } catch (Exception e) {
            log.warn("Failed to add audit comment for alert {}", alert.getId(), e);
        }
    }

    private record PRInfo(String owner, String repo, int number) {}

    private PRInfo parsePRUrl(String url) {
        try {
            String path = url.replace("https://github.com/", "");
            String[] parts = path.split("/");
            if (parts.length >= 4 && "pull".equals(parts[2])) {
                return new PRInfo(parts[0], parts[1], Integer.parseInt(parts[3]));
            }
        } catch (Exception e) {
            log.warn("Failed to parse PR URL: {}", url);
        }
        return null;
    }
}
