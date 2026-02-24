package com.signalspoc.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signalspoc.ai.model.AiActionRecommendation;
import com.signalspoc.ai.model.AiActionRecommendation.ActionType;
import com.signalspoc.connector.github.GitHubApiClient;
import com.signalspoc.connector.github.GitHubConfig;
import com.signalspoc.connector.pm.api.PmConnectorService;
import com.signalspoc.domain.entity.SyncAlert;
import com.signalspoc.domain.repository.SyncAlertRepository;
import com.signalspoc.shared.exception.Exceptions.ResourceNotFoundException;
import com.signalspoc.shared.model.Enums.ConnectorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AlertActionExecutorTest {

    private SyncAlertRepository alertRepository;
    private PmConnectorService asanaConnector;
    private PmConnectorService linearConnector;
    private GitHubApiClient gitHubApiClient;
    private GitHubConfig gitHubConfig;
    private ObjectMapper objectMapper;
    private AlertActionExecutor executor;

    @BeforeEach
    void setUp() {
        alertRepository = mock(SyncAlertRepository.class);
        asanaConnector = mock(PmConnectorService.class);
        linearConnector = mock(PmConnectorService.class);
        gitHubApiClient = mock(GitHubApiClient.class);
        gitHubConfig = mock(GitHubConfig.class);
        objectMapper = new ObjectMapper();

        when(asanaConnector.getConnectorType()).thenReturn(ConnectorType.ASANA);
        when(linearConnector.getConnectorType()).thenReturn(ConnectorType.LINEAR);

        executor = new AlertActionExecutor(
                alertRepository,
                List.of(asanaConnector, linearConnector),
                gitHubApiClient,
                gitHubConfig,
                objectMapper);
        executor.initPmConnectorMap();
    }

    // ─── executeAction: guard clauses ─────────────────────────────────────────

    @Test
    void executeAction_throwsWhenAlertNotFound() {
        when(alertRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executor.executeAction(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void executeAction_returnsFailureForAlreadyResolvedAlert() {
        SyncAlert alert = buildAlert(ConnectorType.ASANA, "task-1", SyncAlert.AlertType.PR_MERGED_TASK_OPEN);
        alert.setIsResolved(true);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));

        var result = executor.executeAction(1L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getDescription()).contains("already resolved");
        verify(asanaConnector, never()).completeTask(any());
    }

    // ─── AI recommendation dispatch ───────────────────────────────────────────

    @Test
    void executeAction_aiRec_completeTask_callsCompleteTaskOnPmConnector() throws Exception {
        AiActionRecommendation rec = AiActionRecommendation.builder()
                .actionType(ActionType.COMPLETE_TASK)
                .targetPlatform(ConnectorType.ASANA)
                .targetEntityId("task-123")
                .reasoning("Task should be closed")
                .build();

        SyncAlert alert = buildAlertWithAiJson(ConnectorType.ASANA, "task-1",
                SyncAlert.AlertType.PR_MERGED_TASK_OPEN, rec);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any())).thenReturn(alert);

        var result = executor.executeAction(1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getActionTaken()).isEqualTo("COMPLETE_TASK");
        verify(asanaConnector).completeTask("task-123");
    }

    @Test
    void executeAction_aiRec_completeTask_usesAlertTargetWhenRecHasNoTarget() throws Exception {
        AiActionRecommendation rec = AiActionRecommendation.builder()
                .actionType(ActionType.COMPLETE_TASK)
                .targetPlatform(null)
                .targetEntityId(null)
                .build();

        SyncAlert alert = buildAlertWithAiJson(ConnectorType.ASANA, "task-fallback",
                SyncAlert.AlertType.PR_MERGED_TASK_OPEN, rec);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any())).thenReturn(alert);

        var result = executor.executeAction(1L);

        assertThat(result.isSuccess()).isTrue();
        verify(asanaConnector).completeTask("task-fallback");
    }

    @Test
    void executeAction_aiRec_completeTask_returnsFailureWhenConnectorUnavailable() throws Exception {
        AiActionRecommendation rec = AiActionRecommendation.builder()
                .actionType(ActionType.COMPLETE_TASK)
                .targetPlatform(ConnectorType.GITHUB) // not a PM connector
                .targetEntityId("pr-1")
                .build();

        SyncAlert alert = buildAlertWithAiJson(ConnectorType.GITHUB, "pr-1",
                SyncAlert.AlertType.PR_MERGED_TASK_OPEN, rec);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));

        var result = executor.executeAction(1L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getDescription()).contains("not available");
    }

    @Test
    void executeAction_aiRec_updateTaskStatus_callsUpdateAndComment() throws Exception {
        AiActionRecommendation rec = AiActionRecommendation.builder()
                .actionType(ActionType.UPDATE_TASK_STATUS)
                .targetPlatform(ConnectorType.LINEAR)
                .targetEntityId("issue-42")
                .parameters(Map.of("status", "In Review", "comment", "PR is ready"))
                .build();

        SyncAlert alert = buildAlertWithAiJson(ConnectorType.LINEAR, "issue-42",
                SyncAlert.AlertType.PR_READY_TASK_NOT_UPDATED, rec);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any())).thenReturn(alert);

        var result = executor.executeAction(1L);

        assertThat(result.isSuccess()).isTrue();
        verify(linearConnector).updateTaskStatus("issue-42", "In Review");
        verify(linearConnector).addComment(eq("issue-42"), contains("PR is ready"));
    }

    @Test
    void executeAction_aiRec_updateTaskStatus_skipsCommentWhenNoPrComment() throws Exception {
        AiActionRecommendation rec = AiActionRecommendation.builder()
                .actionType(ActionType.UPDATE_TASK_STATUS)
                .targetPlatform(ConnectorType.ASANA)
                .targetEntityId("task-5")
                .parameters(Map.of("status", "Done"))
                .build();

        SyncAlert alert = buildAlertWithAiJson(ConnectorType.ASANA, "task-5",
                SyncAlert.AlertType.PR_MERGED_TASK_OPEN, rec);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any())).thenReturn(alert);

        executor.executeAction(1L);

        verify(asanaConnector).updateTaskStatus("task-5", "Done");
        // No PR comment relay — only the audit comment is added
        verify(asanaConnector, times(1)).addComment(eq("task-5"), contains("[Signals] Auto-action executed"));
    }

    @Test
    void executeAction_aiRec_addComment_callsAddCommentOnConnector() throws Exception {
        AiActionRecommendation rec = AiActionRecommendation.builder()
                .actionType(ActionType.ADD_COMMENT)
                .targetPlatform(ConnectorType.ASANA)
                .targetEntityId("task-7")
                .parameters(Map.of("comment", "Please review this"))
                .build();

        SyncAlert alert = buildAlertWithAiJson(ConnectorType.ASANA, "task-7",
                SyncAlert.AlertType.MISSING_LINK, rec);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any())).thenReturn(alert);

        var result = executor.executeAction(1L);

        assertThat(result.isSuccess()).isTrue();
        verify(asanaConnector).addComment(eq("task-7"), contains("Please review this"));
    }

    @Test
    void executeAction_aiRec_noAction_returnsSuccessWithoutCallingConnector() throws Exception {
        AiActionRecommendation rec = AiActionRecommendation.builder()
                .actionType(ActionType.NO_ACTION)
                .reasoning("Everything is fine")
                .build();

        SyncAlert alert = buildAlertWithAiJson(ConnectorType.ASANA, "task-1",
                SyncAlert.AlertType.STATUS_MISMATCH, rec);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any())).thenReturn(alert);

        var result = executor.executeAction(1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getActionTaken()).isEqualTo("NO_ACTION");
        verify(asanaConnector, never()).completeTask(any());
        verify(asanaConnector, never()).updateTaskStatus(any(), any());
        // NO_ACTION should not trigger an audit comment
        verify(asanaConnector, never()).addComment(any(), any());
    }

    @Test
    void executeAction_aiRec_manualReview_returnsFailureWithReasoning() throws Exception {
        AiActionRecommendation rec = AiActionRecommendation.builder()
                .actionType(ActionType.MANUAL_REVIEW)
                .reasoning("Needs human attention")
                .build();

        SyncAlert alert = buildAlertWithAiJson(ConnectorType.ASANA, "task-1",
                SyncAlert.AlertType.MISSING_LINK, rec);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));

        var result = executor.executeAction(1L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getActionTaken()).isEqualTo("MANUAL_REVIEW");
        assertThat(result.getDescription()).contains("Needs human attention");
    }

    @Test
    void executeAction_aiRec_approvePR_callsGitHubApprovePR() throws Exception {
        AiActionRecommendation rec = AiActionRecommendation.builder()
                .actionType(ActionType.APPROVE_PR)
                .parameters(Map.of("body", "LGTM"))
                .build();

        SyncAlert alert = buildAlertWithAiJson(ConnectorType.GITHUB, "pr-99",
                SyncAlert.AlertType.STALE_PR, rec);
        alert.setSourceUrl("https://github.com/owner/repo/pull/42");
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any())).thenReturn(alert);

        var result = executor.executeAction(1L);

        assertThat(result.isSuccess()).isTrue();
        verify(gitHubApiClient).approvePR("owner", "repo", 42, "LGTM");
    }

    @Test
    void executeAction_aiRec_approvePR_returnsFailureWhenGitHubClientUnavailable() throws Exception {
        AiActionRecommendation rec = AiActionRecommendation.builder()
                .actionType(ActionType.APPROVE_PR)
                .build();

        SyncAlert alert = buildAlertWithAiJson(ConnectorType.GITHUB, "pr-1",
                SyncAlert.AlertType.STALE_PR, rec);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));

        AlertActionExecutor noGitHub = new AlertActionExecutor(
                alertRepository, List.of(asanaConnector), null, null, objectMapper);
        noGitHub.initPmConnectorMap();

        var result = noGitHub.executeAction(1L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getDescription()).contains("GitHub connector not available");
    }

    @Test
    void executeAction_aiRec_addPRComment_callsGitHubAddComment() throws Exception {
        AiActionRecommendation rec = AiActionRecommendation.builder()
                .actionType(ActionType.ADD_PR_COMMENT)
                .parameters(Map.of("comment", "Needs changes"))
                .build();

        SyncAlert alert = buildAlertWithAiJson(ConnectorType.GITHUB, "pr-5",
                SyncAlert.AlertType.STALE_PR, rec);
        alert.setSourceUrl("https://github.com/org/project/pull/7");
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any())).thenReturn(alert);

        var result = executor.executeAction(1L);

        assertThat(result.isSuccess()).isTrue();
        verify(gitHubApiClient).addPRComment(eq("org"), eq("project"), eq(7), contains("Needs changes"));
    }

    @Test
    void executeAction_aiRec_updatePRLabels_callsGitHubUpdateLabels() throws Exception {
        AiActionRecommendation rec = AiActionRecommendation.builder()
                .actionType(ActionType.UPDATE_PR_LABELS)
                .parameters(Map.of("labels", "needs-review,blocked"))
                .build();

        SyncAlert alert = buildAlertWithAiJson(ConnectorType.GITHUB, "pr-9",
                SyncAlert.AlertType.STALE_PR, rec);
        alert.setSourceUrl("https://github.com/org/repo/pull/3");
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any())).thenReturn(alert);

        executor.executeAction(1L);

        verify(gitHubApiClient).updatePRLabels(eq("org"), eq("repo"), eq(3), anyList());
    }

    // ─── legacy dispatch ──────────────────────────────────────────────────────

    @Test
    void executeAction_legacyDispatch_prMergedTaskOpen_callsCompleteTaskOnPmConnector() {
        SyncAlert alert = buildAlert(ConnectorType.ASANA, "task-closed",
                SyncAlert.AlertType.PR_MERGED_TASK_OPEN);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any())).thenReturn(alert);

        var result = executor.executeAction(1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getActionTaken()).isEqualTo("COMPLETE_TASK");
        verify(asanaConnector).completeTask("task-closed");
    }

    @Test
    void executeAction_legacyDispatch_prReadyTaskNotUpdated_callsUpdateStatusAndComment() {
        SyncAlert alert = buildAlert(ConnectorType.LINEAR, "issue-88",
                SyncAlert.AlertType.PR_READY_TASK_NOT_UPDATED);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any())).thenReturn(alert);

        var result = executor.executeAction(1L);

        assertThat(result.isSuccess()).isTrue();
        verify(linearConnector).updateTaskStatus("issue-88", "In Review");
        verify(linearConnector).addComment(eq("issue-88"), contains("PR is ready for review"));
    }

    @Test
    void executeAction_legacyDispatch_stalePR_addsGitHubComment() {
        SyncAlert alert = buildAlert(ConnectorType.GITHUB, "pr-99",
                SyncAlert.AlertType.STALE_PR);
        alert.setSourceUrl("https://github.com/myorg/myrepo/pull/15");
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any())).thenReturn(alert);

        var result = executor.executeAction(1L);

        assertThat(result.isSuccess()).isTrue();
        verify(gitHubApiClient).addPRComment(eq("myorg"), eq("myrepo"), eq(15), contains("stale"));
    }

    @Test
    void executeAction_legacyDispatch_missingLink_returnsFailure() {
        SyncAlert alert = buildAlert(ConnectorType.GITHUB, "pr-77",
                SyncAlert.AlertType.MISSING_LINK);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));

        var result = executor.executeAction(1L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getDescription()).contains("manual action");
    }

    @Test
    void executeAction_legacyDispatch_prMergedNoConnector_returnsFailure() {
        SyncAlert alert = buildAlert(ConnectorType.GITHUB, "pr-1",
                SyncAlert.AlertType.PR_MERGED_TASK_OPEN);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));

        var result = executor.executeAction(1L);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getDescription()).contains("not available");
    }

    // ─── alert marking after success ──────────────────────────────────────────

    @Test
    void executeAction_marksAlertAsResolvedAndReadOnSuccess() {
        SyncAlert alert = buildAlert(ConnectorType.ASANA, "task-1",
                SyncAlert.AlertType.PR_MERGED_TASK_OPEN);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any())).thenReturn(alert);

        executor.executeAction(1L);

        assertThat(alert.getIsResolved()).isTrue();
        assertThat(alert.getIsRead()).isTrue();
        assertThat(alert.getResolvedAt()).isNotNull();
    }

    // ─── PR URL parsing ───────────────────────────────────────────────────────

    @Test
    void stalePR_withInvalidPrUrl_returnsFailure() {
        SyncAlert alert = buildAlert(ConnectorType.GITHUB, "pr-1", SyncAlert.AlertType.STALE_PR);
        alert.setSourceUrl("https://not-github.com/invalid");
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));

        var result = executor.executeAction(1L);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void stalePR_withNullSourceUrl_returnsFailure() {
        SyncAlert alert = buildAlert(ConnectorType.GITHUB, "pr-1", SyncAlert.AlertType.STALE_PR);
        alert.setSourceUrl(null);
        when(alertRepository.findById(1L)).thenReturn(Optional.of(alert));

        var result = executor.executeAction(1L);

        assertThat(result.isSuccess()).isFalse();
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private SyncAlert buildAlert(ConnectorType target, String targetId, SyncAlert.AlertType type) {
        return SyncAlert.builder()
                .id(1L)
                .alertType(type)
                .severity(SyncAlert.AlertSeverity.WARNING)
                .title("Test Alert")
                .sourceSystem(ConnectorType.GITHUB)
                .sourceId("pr-1")
                .targetSystem(target)
                .targetId(targetId)
                .isResolved(false)
                .build();
    }

    private SyncAlert buildAlertWithAiJson(ConnectorType target, String targetId,
                                            SyncAlert.AlertType type,
                                            AiActionRecommendation rec) throws Exception {
        SyncAlert alert = buildAlert(target, targetId, type);
        alert.setAiActionJson(objectMapper.writeValueAsString(rec));
        return alert;
    }
}
