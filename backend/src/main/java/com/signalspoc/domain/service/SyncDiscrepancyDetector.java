package com.signalspoc.domain.service;

import com.signalspoc.ai.event.AlertEnrichmentEvent;
import com.signalspoc.domain.entity.SyncAlert;
import com.signalspoc.domain.entity.Task;
import com.signalspoc.domain.repository.TaskRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Detects sync discrepancies between GitHub PRs and Asana/Linear tasks.
 *
 * Detection is purely rule-based and runs every 5 minutes — no AI calls.
 * When a new alert is created, an AlertEnrichmentEvent is published and
 * AiEnrichmentWorker handles the Ollama call on a separate thread.
 */
@Service
@ConditionalOnProperty(name = "connectors.github.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SyncDiscrepancyDetector {

    private final GitHubApiClient gitHubApiClient;
    private final TaskRepository taskRepository;
    private final SyncAlertService alertService;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    public void detectDiscrepancies() {
        log.info("Starting sync discrepancy detection...");
        try {
            List<GitHubPullRequestDto> openPRs = gitHubApiClient.getAllOpenPullRequests();
            log.info("Checking {} open PRs for discrepancies", openPRs.size());
            for (GitHubPullRequestDto pr : openPRs) {
                checkPRDiscrepancies(pr);
            }
            log.info("Sync discrepancy detection completed");
        } catch (Exception e) {
            log.error("Error during discrepancy detection", e);
        }
    }

    public void checkPRDiscrepancies(GitHubPullRequestDto pr) {
        List<String> linkedIssues = gitHubApiClient.extractLinkedIssues(pr);

        if (linkedIssues.isEmpty()) {
            enqueueIfNew(alertService.createAlert(SyncAlert.builder()
                    .alertType(SyncAlert.AlertType.MISSING_LINK)
                    .severity(SyncAlert.AlertSeverity.INFO)
                    .title("PR missing task link")
                    .message(String.format(
                            "PR #%d '%s' does not reference any Asana or Linear task. " +
                            "Consider adding a task reference (e.g., SIG-123) to the PR title or description.",
                            pr.getNumber(), pr.getTitle()))
                    .sourceSystem(ConnectorType.GITHUB)
                    .sourceId(String.valueOf(pr.getId()))
                    .sourceUrl(pr.getHtmlUrl())
                    .build()), pr, null);
            return;
        }

        for (String issueIdentifier : linkedIssues) {
            for (Task task : findAllTasksByIdentifier(issueIdentifier)) {
                checkPRTaskSync(pr, task);
            }
        }

        if (isPRStale(pr)) {
            enqueueIfNew(alertService.createAlert(SyncAlert.builder()
                    .alertType(SyncAlert.AlertType.STALE_PR)
                    .severity(SyncAlert.AlertSeverity.WARNING)
                    .title("Stale PR detected")
                    .message(String.format(
                            "PR #%d '%s' has been open for more than 7 days. " +
                            "Linked tasks: %s. Consider reviewing or closing this PR.",
                            pr.getNumber(), pr.getTitle(), String.join(", ", linkedIssues)))
                    .sourceSystem(ConnectorType.GITHUB)
                    .sourceId(String.valueOf(pr.getId()))
                    .sourceUrl(pr.getHtmlUrl())
                    .targetSystem(ConnectorType.GITHUB)
                    .targetId(String.valueOf(pr.getId()))
                    .build()), pr, null);
        }
    }

    private void checkPRTaskSync(GitHubPullRequestDto pr, Task task) {
        boolean prIsReady  = isPRReadyToMerge(pr);
        boolean prIsMerged = Boolean.TRUE.equals(pr.getMerged());
        boolean prIsOpen   = "open".equals(pr.getState()) && !Boolean.TRUE.equals(pr.getDraft());
        String  status     = task.getStatus() != null ? task.getStatus().toLowerCase() : "";
        boolean inReview   = status.contains("review") || status.contains("in_review");
        boolean done       = status.contains("done") || status.contains("complete") || status.contains("closed");

        // Case 0: PR opened (not yet ready to merge) but task not yet in review
        if (prIsOpen && !prIsReady && !inReview) {
            enqueueIfNew(alertService.createAlert(SyncAlert.builder()
                    .alertType(SyncAlert.AlertType.PR_READY_TASK_NOT_UPDATED)
                    .severity(SyncAlert.AlertSeverity.WARNING)
                    .title("PR opened but task not updated")
                    .message(String.format(
                            "PR #%d '%s' was opened but %s task '%s' is still '%s'. Update to 'In Review'.",
                            pr.getNumber(), pr.getTitle(), task.getSourceSystem(), task.getTitle(), task.getStatus()))
                    .sourceSystem(ConnectorType.GITHUB).sourceId(String.valueOf(pr.getId())).sourceUrl(pr.getHtmlUrl())
                    .targetSystem(task.getSourceSystem()).targetId(task.getExternalId())
                    .build()), pr, task);
        }

        // Case 1: PR ready to merge but task not in review
        if (prIsReady && !inReview && !done) {
            enqueueIfNew(alertService.createAlert(SyncAlert.builder()
                    .alertType(SyncAlert.AlertType.PR_READY_TASK_NOT_UPDATED)
                    .severity(SyncAlert.AlertSeverity.WARNING)
                    .title("PR ready but task not updated")
                    .message(String.format(
                            "PR #%d '%s' is ready to merge but %s task '%s' status is '%s'.",
                            pr.getNumber(), pr.getTitle(), task.getSourceSystem(), task.getTitle(), task.getStatus()))
                    .sourceSystem(ConnectorType.GITHUB).sourceId(String.valueOf(pr.getId())).sourceUrl(pr.getHtmlUrl())
                    .targetSystem(task.getSourceSystem()).targetId(task.getExternalId())
                    .build()), pr, task);
        }

        // Case 2: PR merged but task still open
        if (prIsMerged && !done) {
            enqueueIfNew(alertService.createAlert(SyncAlert.builder()
                    .alertType(SyncAlert.AlertType.PR_MERGED_TASK_OPEN)
                    .severity(SyncAlert.AlertSeverity.CRITICAL)
                    .title("PR merged but task still open")
                    .message(String.format(
                            "PR #%d was merged but %s task '%s' is still '%s'. Mark as complete.",
                            pr.getNumber(), task.getSourceSystem(), task.getTitle(), task.getStatus()))
                    .sourceSystem(ConnectorType.GITHUB).sourceId(String.valueOf(pr.getId())).sourceUrl(pr.getHtmlUrl())
                    .targetSystem(task.getSourceSystem()).targetId(task.getExternalId())
                    .build()), pr, task);
        }
    }

    /**
     * Publishes an enrichment event for new alerts (aiSuggestion == null means newly created,
     * not a deduplicated existing alert that may already have a suggestion).
     */
    private void enqueueIfNew(SyncAlert saved, GitHubPullRequestDto pr, Task task) {
        if (saved.getAiSuggestion() == null) {
            eventPublisher.publishEvent(
                    new AlertEnrichmentEvent(saved.getId(), saved.getAlertType(), saved.getSeverity(), pr, task));
        }
    }

    private boolean isPRReadyToMerge(GitHubPullRequestDto pr) {
        boolean notDraft = !Boolean.TRUE.equals(pr.getDraft());
        boolean isClean  = "clean".equals(pr.getMergeableState()) || "unstable".equals(pr.getMergeableState());
        return notDraft && isClean;
    }

    private boolean isPRStale(GitHubPullRequestDto pr) {
        if (pr.getCreatedAt() == null) return false;
        return ChronoUnit.DAYS.between(pr.getCreatedAt().toLocalDateTime(), LocalDateTime.now()) > 7;
    }

    private List<Task> findAllTasksByIdentifier(String identifier) {
        List<Task> tasks = new java.util.ArrayList<>(taskRepository.findByTitleContaining(identifier));
        Optional<Task> byExternalId = taskRepository.findByExternalId(identifier);
        if (byExternalId.isPresent() && tasks.stream().noneMatch(t -> t.getId().equals(byExternalId.get().getId()))) {
            tasks.add(byExternalId.get());
        }
        return tasks;
    }
}
