package com.signalspoc.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signalspoc.ai.config.AiConfig;
import com.signalspoc.ai.client.OllamaClient;
import com.signalspoc.ai.event.AlertEnrichmentEvent;
import com.signalspoc.ai.model.AnalysisState;
import com.signalspoc.ai.repository.AnalysisStateRepository;
import com.signalspoc.ai.util.AnalysisChecksumUtil;
import com.signalspoc.domain.entity.SyncAlert;
import com.signalspoc.domain.entity.Task;
import com.signalspoc.domain.repository.SyncAlertRepository;
import com.signalspoc.domain.repository.TaskRepository;
import com.signalspoc.domain.service.SyncAlertService;
import com.signalspoc.connector.github.GitHubApiClient;
import com.signalspoc.connector.github.dto.GitHubPullRequestDto;
import com.signalspoc.shared.model.Enums.ConnectorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Runs every 30 minutes (configurable via ai.ollama.reconciliation-interval-ms).
 *
 * Two responsibilities:
 *  1. Reconciliation — finds alerts that slipped through the event worker
 *     (aiSuggestion IS NULL) and re-publishes an AlertEnrichmentEvent for them.
 *  2. Semantic analysis — deep batch check of changed PR-task pairs for issues
 *     that rule-based detection misses (semantic mismatch, assignee drift, etc.).
 */
@Service
@ConditionalOnProperty(name = "ai.ollama.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AiAnalysisScheduler {

    private final OllamaClient ollamaClient;
    private final AiConfig aiConfig;
    private final GitHubApiClient gitHubApiClient;
    private final TaskRepository taskRepository;
    private final SyncAlertService alertService;
    private final SyncAlertRepository alertRepository;
    private final AnalysisStateRepository analysisStateRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${ai.ollama.reconciliation-interval-ms:1800000}", initialDelay = 120000)
    public void runReconciliationAndAnalysis() {
        log.info("Starting AI reconciliation and semantic analysis...");
        try {
            reconcileUnenrichedAlerts();
            runSemanticBatchAnalysis();
        } catch (Exception e) {
            log.error("Error during AI reconciliation", e);
        }
    }

    // ── Part 1: Reconciliation ─────────────────────────────────────────────────

    private void reconcileUnenrichedAlerts() {
        List<SyncAlert> missed = alertRepository.findByAiSuggestionIsNullAndIsResolvedFalseOrderByCreatedAtAsc();
        if (missed.isEmpty()) return;

        log.info("Reconciling {} unenriched alert(s)", missed.size());
        for (SyncAlert alert : missed) {
            // Re-publish without PR/task context — AiEnrichmentWorker will use template fallback
            eventPublisher.publishEvent(
                    new AlertEnrichmentEvent(alert.getId(), alert.getAlertType(), alert.getSeverity(), null, null));
        }
    }

    // ── Part 2: Semantic batch analysis ───────────────────────────────────────

    private void runSemanticBatchAnalysis() {
        List<GitHubPullRequestDto> openPRs = gitHubApiClient.getAllOpenPullRequests();
        List<PrTaskPair> changedPairs = detectChangedPairs(openPRs);

        if (changedPairs.isEmpty()) {
            log.debug("Semantic analysis: no changed PR-task pairs");
            return;
        }

        log.info("Semantic analysis: {} changed pair(s)", changedPairs.size());
        int batchSize = aiConfig.getAnalysisBatchSize();
        for (int i = 0; i < changedPairs.size(); i += batchSize) {
            analyzeBatch(changedPairs.subList(i, Math.min(i + batchSize, changedPairs.size())));
        }
    }

    private List<PrTaskPair> detectChangedPairs(List<GitHubPullRequestDto> openPRs) {
        List<PrTaskPair> changed = new ArrayList<>();
        for (GitHubPullRequestDto pr : openPRs) {
            for (String id : gitHubApiClient.extractLinkedIssues(pr)) {
                for (Task task : findTasksByIdentifier(id)) {
                    String entityId = AnalysisChecksumUtil.buildEntityId(pr, task);
                    String checksum = AnalysisChecksumUtil.computeChecksum(pr, task);
                    Optional<AnalysisState> existing = analysisStateRepository
                            .findByEntityTypeAndEntityId("PR_TASK_PAIR", entityId);
                    if (existing.isEmpty() || !checksum.equals(existing.get().getContentChecksum())) {
                        changed.add(new PrTaskPair(pr, task, entityId, checksum));
                    }
                }
            }
        }
        return changed;
    }

    private void analyzeBatch(List<PrTaskPair> batch) {
        String prompt = buildBatchAnalysisPrompt(batch);
        try {
            String response = ollamaClient.generateStructuredResponse(prompt, aiConfig.getAnalysisMaxTokens());
            if (response != null && !response.isBlank()) {
                processAnalysisResponse(response, batch);
            }
        } catch (Exception e) {
            log.warn("Semantic batch analysis failed: {}", e.getMessage());
        }
        batch.forEach(this::updateAnalysisState);
    }

    private String buildBatchAnalysisPrompt(List<PrTaskPair> batch) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Signals POC background analysis — check these ").append(batch.size())
              .append(" GitHub PR / task pair(s) for sync discrepancies.\n\n");

        prompt.append("Look for:\n");
        prompt.append("  STATUS_MISMATCH   — PR state and task status don't logically align\n");
        prompt.append("                      (e.g. PR merged but task still In Progress)\n");
        prompt.append("  ASSIGNEE_MISMATCH — PR author differs from task assignee\n");
        prompt.append("  STALE_PR          — PR open 7+ days without merge or close\n");
        prompt.append("  MISSING_LINK      — PR title/body has no recognisable issue ID\n\n");
        prompt.append("Severity guide: CRITICAL = blocks delivery, WARNING = needs attention soon, INFO = informational.\n\n");

        for (int i = 0; i < batch.size(); i++) {
            PrTaskPair pair = batch.get(i);
            GitHubPullRequestDto pr = pair.pr;
            Task task = pair.task;

            prompt.append("Pair ").append(i + 1).append(":\n");
            prompt.append("  PR #").append(pr.getNumber()).append(": \"").append(pr.getTitle()).append("\"");
            prompt.append(" (state=").append(pr.getState())
                  .append(", merged=").append(pr.getMerged())
                  .append(", draft=").append(pr.getDraft());
            if (pr.getUser() != null) prompt.append(", author=").append(pr.getUser().getLogin());
            prompt.append(")\n");

            prompt.append("  Task [").append(task.getSourceSystem()).append(" ").append(task.getExternalId())
                  .append("]: \"").append(task.getTitle()).append("\"");
            prompt.append(" (status=").append(task.getStatus());
            if (task.getAssignee() != null) prompt.append(", assignee=").append(task.getAssignee().getName());
            prompt.append(")\n\n");
        }

        prompt.append("Return JSON only:\n");
        prompt.append("{\"findings\": [{\"pairIndex\": 1, \"alertType\": \"STATUS_MISMATCH\", ");
        prompt.append("\"severity\": \"WARNING\", \"title\": \"short title\", \"message\": \"detail\"}]}\n");
        prompt.append("alertType must be one of: STATUS_MISMATCH, ASSIGNEE_MISMATCH, STALE_PR, MISSING_LINK\n");
        prompt.append("severity must be one of: INFO, WARNING, CRITICAL\n");
        prompt.append("If no issues found: {\"findings\": []}");

        return prompt.toString();
    }

    private void processAnalysisResponse(String response, List<PrTaskPair> batch) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(response, new TypeReference<>() {});
            Object findingsObj = parsed.get("findings");
            if (findingsObj == null) return;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> findings = (List<Map<String, Object>>) findingsObj;

            for (Map<String, Object> finding : findings) {
                int idx = ((Number) finding.getOrDefault("pairIndex", 0)).intValue() - 1;
                if (idx < 0 || idx >= batch.size()) continue;

                PrTaskPair pair = batch.get(idx);
                SyncAlert.AlertType alertType = parseAlertType((String) finding.getOrDefault("alertType", "STATUS_MISMATCH"));
                SyncAlert.AlertSeverity severity = parseSeverity((String) finding.getOrDefault("severity", "INFO"));

                SyncAlert saved = alertService.createAlert(SyncAlert.builder()
                        .alertType(alertType)
                        .severity(severity)
                        .title((String) finding.getOrDefault("title", "AI-detected sync issue"))
                        .message((String) finding.getOrDefault("message", ""))
                        .sourceSystem(ConnectorType.GITHUB)
                        .sourceId(String.valueOf(pair.pr.getId()))
                        .sourceUrl(pair.pr.getHtmlUrl())
                        .targetSystem(pair.task.getSourceSystem())
                        .targetId(pair.task.getExternalId())
                        .build());

                if (saved.getAiSuggestion() == null) {
                    eventPublisher.publishEvent(new AlertEnrichmentEvent(
                            saved.getId(), alertType, severity, pair.pr, pair.task));
                }

                log.info("Semantic analysis created alert: {} for PR #{}", alertType, pair.pr.getNumber());
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse semantic analysis response: {}", e.getMessage());
        }
    }

    private void updateAnalysisState(PrTaskPair pair) {
        Optional<AnalysisState> existing = analysisStateRepository
                .findByEntityTypeAndEntityId("PR_TASK_PAIR", pair.entityId);
        AnalysisState state = existing.orElseGet(() -> {
            AnalysisState s = new AnalysisState();
            s.setEntityType("PR_TASK_PAIR");
            s.setEntityId(pair.entityId);
            s.setSourceSystem(pair.task.getSourceSystem().name());
            return s;
        });
        state.setContentChecksum(pair.checksum);
        state.setLastAnalyzedAt(LocalDateTime.now());
        analysisStateRepository.save(state);
    }

    private List<Task> findTasksByIdentifier(String identifier) {
        List<Task> tasks = new ArrayList<>(taskRepository.findByTitleContaining(identifier));
        taskRepository.findByExternalId(identifier).ifPresent(t -> {
            if (tasks.stream().noneMatch(existing -> existing.getId().equals(t.getId()))) tasks.add(t);
        });
        return tasks;
    }

    private SyncAlert.AlertType parseAlertType(String type) {
        try { return SyncAlert.AlertType.valueOf(type); }
        catch (IllegalArgumentException e) { return SyncAlert.AlertType.STATUS_MISMATCH; }
    }

    private SyncAlert.AlertSeverity parseSeverity(String severity) {
        try { return SyncAlert.AlertSeverity.valueOf(severity); }
        catch (IllegalArgumentException e) { return SyncAlert.AlertSeverity.INFO; }
    }

    private record PrTaskPair(GitHubPullRequestDto pr, Task task, String entityId, String checksum) {}
}
