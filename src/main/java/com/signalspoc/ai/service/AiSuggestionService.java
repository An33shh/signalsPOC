package com.signalspoc.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signalspoc.ai.client.OllamaClient;
import com.signalspoc.ai.config.AiConfig;
import com.signalspoc.ai.model.AiActionRecommendation;
import com.signalspoc.domain.entity.SyncAlert;
import com.signalspoc.domain.entity.Task;
import com.signalspoc.connector.github.dto.GitHubPullRequestDto;
import com.signalspoc.shared.model.Enums.ConnectorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@ConditionalOnProperty(name = "ai.ollama.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AiSuggestionService {

    private final OllamaClient ollamaClient;
    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper;

    private boolean isProductModel() {
        return aiConfig.getModel().startsWith("signals-poc");
    }

    public String generateAlertSuggestion(SyncAlert.AlertType alertType,
                                           SyncAlert.AlertSeverity severity,
                                           GitHubPullRequestDto pr,
                                           Task task) {
        String prompt = buildPrompt(alertType, severity, pr, task);

        try {
            String aiResponse = ollamaClient.generateSuggestion(prompt);
            if (aiResponse != null && !aiResponse.isBlank()) {
                log.debug("AI generated suggestion for {} alert", alertType);
                return aiResponse.strip();
            }
        } catch (Exception e) {
            log.warn("AI suggestion generation failed, using template fallback", e);
        }

        // Fallback to template-based suggestion
        return null;
    }

    public AiActionRecommendation generateActionRecommendation(SyncAlert.AlertType alertType,
                                                                SyncAlert.AlertSeverity severity,
                                                                GitHubPullRequestDto pr,
                                                                Task task) {
        String prompt = buildActionPrompt(alertType, severity, pr, task);

        try {
            String jsonResponse = ollamaClient.generateStructuredResponse(prompt, aiConfig.getAnalysisMaxTokens());
            if (jsonResponse != null && !jsonResponse.isBlank()) {
                AiActionRecommendation rec = objectMapper.readValue(jsonResponse, AiActionRecommendation.class);
                log.debug("AI generated action recommendation: {} (confidence: {})", rec.getActionType(), rec.getConfidence());
                return rec;
            }
        } catch (Exception e) {
            log.warn("AI action recommendation failed, using template fallback", e);
        }

        return buildTemplateFallback(alertType, severity, pr, task);
    }

    private String buildActionPrompt(SyncAlert.AlertType alertType,
                                      SyncAlert.AlertSeverity severity,
                                      GitHubPullRequestDto pr,
                                      Task task) {
        StringBuilder prompt = new StringBuilder();

        // Discrepancy data
        prompt.append("Signals POC alert — type: ").append(alertType)
              .append(", severity: ").append(severity).append("\n\n");

        if (pr != null) {
            prompt.append("GitHub PR #").append(pr.getNumber()).append(": \"").append(pr.getTitle()).append("\"\n");
            prompt.append("  state=").append(pr.getState());
            prompt.append(", draft=").append(pr.getDraft());
            prompt.append(", merged=").append(pr.getMerged());
            if (pr.getUser() != null) prompt.append(", author=").append(pr.getUser().getLogin());
            prompt.append("\n");
        }

        if (task != null) {
            prompt.append("Linked ").append(task.getSourceSystem())
                  .append(" task (id=").append(task.getExternalId()).append("): \"").append(task.getTitle()).append("\"\n");
            prompt.append("  status=").append(task.getStatus());
            if (task.getAssignee() != null) prompt.append(", assignee=").append(task.getAssignee().getName());
            prompt.append("\n");
        }

        // Action reference — omit on signals-poc model since it's in the baked-in system prompt
        if (!isProductModel()) {
            prompt.append("\nChoose one action:\n");
            prompt.append("  UPDATE_TASK_STATUS — move Asana/Linear task to a new status\n");
            prompt.append("    Asana statuses: In Progress, In Review, Complete, Blocked\n");
            prompt.append("    Linear statuses: In Progress, In Review, Done, Cancelled, Backlog\n");
            prompt.append("    parameters: {\"status\": \"<new status>\", \"comment\": \"<PR URL or context>\"}\n");
            prompt.append("  COMPLETE_TASK     — mark Asana/Linear task as done; parameters: {}\n");
            prompt.append("  ADD_COMMENT       — add comment to Asana/Linear task; parameters: {\"comment\": \"<text>\"}\n");
            prompt.append("  ADD_PR_COMMENT    — post comment on GitHub PR; parameters: {\"comment\": \"<text>\"}\n");
            prompt.append("  UPDATE_PR_LABELS  — set GitHub PR labels; parameters: {\"labels\": \"<comma-separated>\"}\n");
            prompt.append("  APPROVE_PR        — submit GitHub approved review (only if PR is ready and checks pass)\n");
            prompt.append("                      parameters: {\"body\": \"<review message>\"}\n");
            prompt.append("  NO_ACTION         — no automated action needed; parameters: {}\n");
            prompt.append("  MANUAL_REVIEW     — human decision required; parameters: {}\n");
            prompt.append("\n");
        }

        // Few-shot example
        prompt.append("Example (PR_MERGED_TASK_OPEN on Asana task 98765):\n");
        prompt.append("{\"actionType\":\"COMPLETE_TASK\",\"targetPlatform\":\"ASANA\",\"targetEntityId\":\"98765\",");
        prompt.append("\"parameters\":{},\"reasoning\":\"PR merged; marking linked Asana task complete.\",\"confidence\":0.95}\n\n");

        prompt.append("Now output JSON only for the current alert:");

        return prompt.toString();
    }

    private AiActionRecommendation buildTemplateFallback(SyncAlert.AlertType alertType,
                                                          SyncAlert.AlertSeverity severity,
                                                          GitHubPullRequestDto pr,
                                                          Task task) {
        ConnectorType taskPlatform = task != null ? task.getSourceSystem() : null;
        String taskId = task != null ? task.getExternalId() : null;

        return switch (alertType) {
            case PR_MERGED_TASK_OPEN -> AiActionRecommendation.builder()
                    .actionType(AiActionRecommendation.ActionType.COMPLETE_TASK)
                    .targetPlatform(taskPlatform)
                    .targetEntityId(taskId)
                    .parameters(Map.of())
                    .reasoning("PR has been merged but the linked task is still open. Auto-completing the task.")
                    .confidence(0.9)
                    .build();
            case PR_READY_TASK_NOT_UPDATED -> AiActionRecommendation.builder()
                    .actionType(AiActionRecommendation.ActionType.UPDATE_TASK_STATUS)
                    .targetPlatform(taskPlatform)
                    .targetEntityId(taskId)
                    .parameters(pr != null
                            ? Map.of("status", "In Review",
                                     "comment", String.format("PR #%d opened: %s", pr.getNumber(), pr.getHtmlUrl()))
                            : Map.of("status", "In Review"))
                    .reasoning("PR is open but task status has not been updated to 'In Review'.")
                    .confidence(0.85)
                    .build();
            case STALE_PR -> AiActionRecommendation.builder()
                    .actionType(AiActionRecommendation.ActionType.ADD_PR_COMMENT)
                    .targetPlatform(ConnectorType.GITHUB)
                    .targetEntityId(pr != null ? String.valueOf(pr.getNumber()) : null)
                    .parameters(Map.of("comment", "This PR has been open for more than 7 days. Please review or close if no longer needed."))
                    .reasoning("PR is stale and needs attention.")
                    .confidence(0.8)
                    .build();
            default -> AiActionRecommendation.builder()
                    .actionType(AiActionRecommendation.ActionType.MANUAL_REVIEW)
                    .targetPlatform(taskPlatform)
                    .targetEntityId(taskId)
                    .parameters(Map.of())
                    .reasoning("This alert type requires manual review.")
                    .confidence(0.5)
                    .build();
        };
    }

    private String buildPrompt(SyncAlert.AlertType alertType,
                                SyncAlert.AlertSeverity severity,
                                GitHubPullRequestDto pr,
                                Task task) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Signals POC detected a ").append(severity).append("-severity sync discrepancy: ").append(alertType).append(".\n\n");

        if (pr != null) {
            prompt.append("GitHub PR #").append(pr.getNumber()).append(": \"").append(pr.getTitle()).append("\"\n");
            prompt.append("  state=").append(pr.getState());
            prompt.append(", draft=").append(pr.getDraft());
            prompt.append(", merged=").append(pr.getMerged());
            if (pr.getUser() != null) prompt.append(", author=").append(pr.getUser().getLogin());
            if (pr.getHead() != null) prompt.append(", branch=").append(pr.getHead().getRef());
            prompt.append("\n");
        }

        if (task != null) {
            prompt.append("Linked ").append(task.getSourceSystem()).append(" task: \"").append(task.getTitle()).append("\"\n");
            prompt.append("  status=").append(task.getStatus());
            if (task.getAssignee() != null) prompt.append(", assignee=").append(task.getAssignee().getName());
            prompt.append("\n");
        }

        prompt.append("\nIn 2-3 sentences, describe the exact action to take: which platform to update, ")
              .append("what status to set, and why. Be specific.");

        return prompt.toString();
    }
}
