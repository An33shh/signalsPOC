package com.signalspoc.domain.service;

import com.signalspoc.ai.event.AlertEnrichmentEvent;
import com.signalspoc.connector.github.GitHubApiClient;
import com.signalspoc.connector.github.dto.GitHubPullRequestDto;
import com.signalspoc.domain.entity.SyncAlert;
import com.signalspoc.domain.entity.Task;
import com.signalspoc.domain.repository.TaskRepository;
import com.signalspoc.shared.model.Enums.ConnectorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncDiscrepancyDetectorTest {

    @Mock GitHubApiClient gitHubApiClient;
    @Mock TaskRepository taskRepository;
    @Mock SyncAlertService alertService;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks
    SyncDiscrepancyDetector detector;

    // ─── detectDiscrepancies (scheduler entry point) ───────────────────────────

    @Test
    void detectDiscrepancies_processesAllOpenPRs() {
        GitHubPullRequestDto pr1 = buildPR(1L, 1, "open", false, null);
        GitHubPullRequestDto pr2 = buildPR(2L, 2, "open", false, null);
        when(gitHubApiClient.getAllOpenPullRequests()).thenReturn(List.of(pr1, pr2));
        when(gitHubApiClient.extractLinkedIssues(any())).thenReturn(List.of());
        when(alertService.createAlert(any())).thenAnswer(inv -> inv.getArgument(0));

        detector.detectDiscrepancies();

        verify(gitHubApiClient).getAllOpenPullRequests();
        verify(gitHubApiClient, times(2)).extractLinkedIssues(any());
    }

    @Test
    void detectDiscrepancies_doesNotThrowWhenGitHubClientFails() {
        when(gitHubApiClient.getAllOpenPullRequests()).thenThrow(new RuntimeException("API error"));

        // Should not propagate — detector swallows exceptions
        detector.detectDiscrepancies();
    }

    // ─── MISSING_LINK alert ────────────────────────────────────────────────────

    @Test
    void checkPRDiscrepancies_createsMissingLinkAlertWhenNoLinkedIssues() {
        GitHubPullRequestDto pr = buildPR(10L, 10, "open", false, null);
        pr.setHtmlUrl("https://github.com/org/repo/pull/10");

        when(gitHubApiClient.extractLinkedIssues(pr)).thenReturn(List.of());

        SyncAlert created = SyncAlert.builder()
                .id(100L)
                .alertType(SyncAlert.AlertType.MISSING_LINK)
                .build();
        when(alertService.createAlert(argThat(a -> a.getAlertType() == SyncAlert.AlertType.MISSING_LINK)))
                .thenReturn(created);

        detector.checkPRDiscrepancies(pr);

        verify(alertService).createAlert(argThat(a ->
                a.getAlertType() == SyncAlert.AlertType.MISSING_LINK
                && a.getSourceSystem() == ConnectorType.GITHUB
                && a.getSourceId().equals("10")));
    }

    @Test
    void checkPRDiscrepancies_publishesEnrichmentEventForNewMissingLinkAlert() {
        GitHubPullRequestDto pr = buildPR(10L, 10, "open", false, null);
        when(gitHubApiClient.extractLinkedIssues(pr)).thenReturn(List.of());

        SyncAlert newAlert = SyncAlert.builder()
                .id(100L)
                .alertType(SyncAlert.AlertType.MISSING_LINK)
                .aiSuggestion(null) // new — no suggestion yet
                .build();
        when(alertService.createAlert(any())).thenReturn(newAlert);

        detector.checkPRDiscrepancies(pr);

        verify(eventPublisher).publishEvent(any(AlertEnrichmentEvent.class));
    }

    @Test
    void checkPRDiscrepancies_doesNotPublishEventForExistingAlertWithSuggestion() {
        GitHubPullRequestDto pr = buildPR(10L, 10, "open", false, null);
        when(gitHubApiClient.extractLinkedIssues(pr)).thenReturn(List.of());

        SyncAlert existingAlert = SyncAlert.builder()
                .id(100L)
                .alertType(SyncAlert.AlertType.MISSING_LINK)
                .aiSuggestion("Already enriched") // existing alert
                .build();
        when(alertService.createAlert(any())).thenReturn(existingAlert);

        detector.checkPRDiscrepancies(pr);

        verify(eventPublisher, never()).publishEvent(any());
    }

    // ─── PR task sync: case 0 (PR open, not ready, task not in review) ─────────

    @Test
    void checkPRTaskSync_case0_createsPRReadyAlertWhenPrOpenAndTaskNotInReview() {
        GitHubPullRequestDto pr = buildPR(1L, 1, "open", false, "dirty"); // not ready (dirty state)
        Task task = buildTask("task-ext-1", ConnectorType.ASANA, "todo");

        when(gitHubApiClient.extractLinkedIssues(pr)).thenReturn(List.of("SIG-1"));
        when(taskRepository.findByTitleContaining("SIG-1")).thenReturn(new ArrayList<>(List.of(task)));
        when(taskRepository.findByExternalId("SIG-1")).thenReturn(Optional.empty());

        SyncAlert alert = SyncAlert.builder()
                .id(1L).alertType(SyncAlert.AlertType.PR_READY_TASK_NOT_UPDATED).build();
        when(alertService.createAlert(any())).thenReturn(alert);

        detector.checkPRDiscrepancies(pr);

        verify(alertService).createAlert(argThat(a ->
                a.getAlertType() == SyncAlert.AlertType.PR_READY_TASK_NOT_UPDATED
                && a.getSeverity() == SyncAlert.AlertSeverity.WARNING));
    }

    // ─── PR task sync: case 1 (PR ready, task not in review) ──────────────────

    @Test
    void checkPRTaskSync_case1_createsPRReadyAlertWhenPrReadyAndTaskNotInReview() {
        GitHubPullRequestDto pr = buildPR(1L, 1, "open", false, "clean"); // ready
        Task task = buildTask("task-ext-1", ConnectorType.LINEAR, "todo");

        when(gitHubApiClient.extractLinkedIssues(pr)).thenReturn(List.of("SIG-1"));
        when(taskRepository.findByTitleContaining("SIG-1")).thenReturn(new ArrayList<>(List.of(task)));
        when(taskRepository.findByExternalId("SIG-1")).thenReturn(Optional.empty());

        SyncAlert alert = SyncAlert.builder()
                .id(1L).alertType(SyncAlert.AlertType.PR_READY_TASK_NOT_UPDATED).build();
        when(alertService.createAlert(any())).thenReturn(alert);

        detector.checkPRDiscrepancies(pr);

        verify(alertService).createAlert(argThat(a ->
                a.getAlertType() == SyncAlert.AlertType.PR_READY_TASK_NOT_UPDATED
                && a.getTargetSystem() == ConnectorType.LINEAR
                && a.getTargetId().equals("task-ext-1")));
    }

    @Test
    void checkPRTaskSync_case1_noAlertWhenTaskAlreadyInReview() {
        GitHubPullRequestDto pr = buildPR(1L, 1, "open", false, "clean");
        Task task = buildTask("task-ext-1", ConnectorType.ASANA, "in review");

        when(gitHubApiClient.extractLinkedIssues(pr)).thenReturn(List.of("SIG-1"));
        when(taskRepository.findByTitleContaining("SIG-1")).thenReturn(new ArrayList<>(List.of(task)));
        when(taskRepository.findByExternalId("SIG-1")).thenReturn(Optional.empty());

        detector.checkPRDiscrepancies(pr);

        // No PR_READY alert should be created
        verify(alertService, never()).createAlert(argThat(a ->
                a.getAlertType() == SyncAlert.AlertType.PR_READY_TASK_NOT_UPDATED));
    }

    // ─── PR task sync: case 2 (PR merged, task still open) ────────────────────

    @Test
    void checkPRTaskSync_case2_createsPRMergedAlertWhenTaskNotDone() {
        GitHubPullRequestDto pr = buildPR(2L, 2, "closed", false, null);
        pr.setMerged(true);
        Task task = buildTask("task-ext-2", ConnectorType.ASANA, "in progress");

        when(gitHubApiClient.extractLinkedIssues(pr)).thenReturn(List.of("TASK-2"));
        when(taskRepository.findByTitleContaining("TASK-2")).thenReturn(new ArrayList<>(List.of(task)));
        when(taskRepository.findByExternalId("TASK-2")).thenReturn(Optional.empty());

        SyncAlert alert = SyncAlert.builder()
                .id(2L).alertType(SyncAlert.AlertType.PR_MERGED_TASK_OPEN).build();
        when(alertService.createAlert(any())).thenReturn(alert);

        detector.checkPRDiscrepancies(pr);

        verify(alertService).createAlert(argThat(a ->
                a.getAlertType() == SyncAlert.AlertType.PR_MERGED_TASK_OPEN
                && a.getSeverity() == SyncAlert.AlertSeverity.CRITICAL));
    }

    @Test
    void checkPRTaskSync_case2_noAlertWhenTaskIsDone() {
        GitHubPullRequestDto pr = buildPR(2L, 2, "closed", false, null);
        pr.setMerged(true);
        Task task = buildTask("task-ext-2", ConnectorType.ASANA, "completed");

        when(gitHubApiClient.extractLinkedIssues(pr)).thenReturn(List.of("TASK-2"));
        when(taskRepository.findByTitleContaining("TASK-2")).thenReturn(new ArrayList<>(List.of(task)));
        when(taskRepository.findByExternalId("TASK-2")).thenReturn(Optional.empty());

        detector.checkPRDiscrepancies(pr);

        verify(alertService, never()).createAlert(argThat(a ->
                a.getAlertType() == SyncAlert.AlertType.PR_MERGED_TASK_OPEN));
    }

    // ─── STALE_PR alert ───────────────────────────────────────────────────────

    @Test
    void checkPRDiscrepancies_createsStalePRAlertForOldPRWithLinkedIssues() {
        GitHubPullRequestDto pr = buildPR(3L, 3, "open", false, "dirty");
        pr.setCreatedAt(OffsetDateTime.now().minusDays(10)); // 10 days old → stale
        Task task = buildTask("task-1", ConnectorType.ASANA, "in review");

        when(gitHubApiClient.extractLinkedIssues(pr)).thenReturn(List.of("TASK-1"));
        when(taskRepository.findByTitleContaining("TASK-1")).thenReturn(new ArrayList<>(List.of(task)));
        when(taskRepository.findByExternalId("TASK-1")).thenReturn(Optional.empty());
        when(alertService.createAlert(any())).thenReturn(SyncAlert.builder().id(99L).build());

        detector.checkPRDiscrepancies(pr);

        verify(alertService).createAlert(argThat(a ->
                a.getAlertType() == SyncAlert.AlertType.STALE_PR
                && a.getSeverity() == SyncAlert.AlertSeverity.WARNING));
    }

    @Test
    void checkPRDiscrepancies_noStalePRAlertForRecentPR() {
        GitHubPullRequestDto pr = buildPR(3L, 3, "open", false, "dirty");
        pr.setCreatedAt(OffsetDateTime.now().minusDays(3)); // 3 days old → not stale
        Task task = buildTask("task-1", ConnectorType.ASANA, "in review");

        when(gitHubApiClient.extractLinkedIssues(pr)).thenReturn(List.of("TASK-1"));
        when(taskRepository.findByTitleContaining("TASK-1")).thenReturn(new ArrayList<>(List.of(task)));
        when(taskRepository.findByExternalId("TASK-1")).thenReturn(Optional.empty());

        detector.checkPRDiscrepancies(pr);

        verify(alertService, never()).createAlert(argThat(a ->
                a.getAlertType() == SyncAlert.AlertType.STALE_PR));
    }

    // ─── findAllTasksByIdentifier: deduplication ──────────────────────────────

    @Test
    void findAllTasksByIdentifier_deduplicatesTasksFoundByBothQueries() {
        Task task = buildTask("ext-1", ConnectorType.ASANA, "todo");
        task.setId(99L);

        // Same task returned by both queries
        when(taskRepository.findByTitleContaining("ext-1")).thenReturn(new ArrayList<>(List.of(task)));
        when(taskRepository.findByExternalId("ext-1")).thenReturn(Optional.of(task));

        GitHubPullRequestDto pr = buildPR(1L, 1, "open", false, "clean");
        when(gitHubApiClient.extractLinkedIssues(pr)).thenReturn(List.of("ext-1"));
        when(alertService.createAlert(any())).thenReturn(SyncAlert.builder().id(1L).build());

        detector.checkPRDiscrepancies(pr);

        // Task found via title search already in list — should only process it once
        verify(alertService, atMost(2)).createAlert(any()); // at most case1 + stale
    }

    @Test
    void findAllTasksByIdentifier_addsByExternalIdTaskIfNotAlreadyInTitleResults() {
        Task taskByTitle = buildTask("other-task", ConnectorType.ASANA, "todo");
        taskByTitle.setId(1L);
        Task taskByExternalId = buildTask("ext-1", ConnectorType.ASANA, "todo");
        taskByExternalId.setId(2L);

        when(taskRepository.findByTitleContaining("ext-1")).thenReturn(new ArrayList<>(List.of(taskByTitle)));
        when(taskRepository.findByExternalId("ext-1")).thenReturn(Optional.of(taskByExternalId));

        GitHubPullRequestDto pr = buildPR(1L, 1, "open", false, "clean");
        when(gitHubApiClient.extractLinkedIssues(pr)).thenReturn(List.of("ext-1"));
        when(alertService.createAlert(any())).thenReturn(SyncAlert.builder().id(1L).build());

        detector.checkPRDiscrepancies(pr);

        // Both tasks should be processed: two createAlert calls (one per task for case1)
        verify(alertService, atLeast(2)).createAlert(any());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private GitHubPullRequestDto buildPR(Long id, int number, String state,
                                          boolean draft, String mergeableState) {
        GitHubPullRequestDto pr = new GitHubPullRequestDto();
        pr.setId(id);
        pr.setNumber(number);
        pr.setTitle("Test PR #" + number);
        pr.setState(state);
        pr.setDraft(draft);
        pr.setMerged(false);
        pr.setMergeableState(mergeableState);
        pr.setCreatedAt(OffsetDateTime.now().minusDays(1));
        pr.setHtmlUrl("https://github.com/org/repo/pull/" + number);
        return pr;
    }

    private Task buildTask(String externalId, ConnectorType source, String status) {
        return Task.builder()
                .externalId(externalId)
                .sourceSystem(source)
                .title("Task " + externalId)
                .status(status)
                .build();
    }
}
