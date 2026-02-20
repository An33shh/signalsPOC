package com.signalspoc.connector.github;

import com.signalspoc.connector.model.ConnectorComment;
import com.signalspoc.connector.model.ConnectorProject;
import com.signalspoc.connector.model.ConnectorTask;
import com.signalspoc.connector.model.ConnectorUser;
import com.signalspoc.connector.github.dto.GitHubPullRequestDto;
import com.signalspoc.connector.github.dto.GitHubRepositoryDto;
import com.signalspoc.connector.github.dto.GitHubUserDto;
import com.signalspoc.shared.model.Enums.ConnectorType;
import com.signalspoc.shared.model.Enums.Priority;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;

@Component
public class GitHubMapper {

    public ConnectorTask toConnectorTask(GitHubPullRequestDto pr) {
        if (pr == null) return null;

        String status;
        if (Boolean.TRUE.equals(pr.getMerged())) {
            status = "merged";
        } else if ("closed".equals(pr.getState())) {
            status = "closed";
        } else if (Boolean.TRUE.equals(pr.getDraft())) {
            status = "draft";
        } else {
            status = "open";
        }

        String branchName = pr.getHead() != null ? pr.getHead().getRef() : null;
        String repoFullName = pr.getBase() != null && pr.getBase().getRepo() != null
                ? pr.getBase().getRepo().getFullName() : null;

        return ConnectorTask.builder()
                .externalId(String.valueOf(pr.getId()))
                .sourceSystem(ConnectorType.GITHUB)
                .projectExternalId(repoFullName)
                .title(String.format("PR #%d: %s", pr.getNumber(), pr.getTitle()))
                .description(pr.getBody())
                .status(status)
                .priority(Priority.MEDIUM)
                .assigneeExternalId(pr.getAssignee() != null ? String.valueOf(pr.getAssignee().getId()) : null)
                .createdAt(toLocalDateTime(pr.getCreatedAt()))
                .modifiedAt(toLocalDateTime(pr.getUpdatedAt()))
                .externalUrl(pr.getHtmlUrl())
                .branchName(branchName)
                .build();
    }

    public ConnectorUser toConnectorUser(GitHubUserDto user) {
        if (user == null) return null;

        return ConnectorUser.builder()
                .externalId(String.valueOf(user.getId()))
                .sourceSystem(ConnectorType.GITHUB)
                .name(user.getName() != null ? user.getName() : user.getLogin())
                .email(user.getEmail())
                .isActive(true)
                .build();
    }

    public ConnectorProject toConnectorProject(GitHubRepositoryDto repo) {
        if (repo == null) return null;

        return ConnectorProject.builder()
                .externalId(repo.getFullName())
                .sourceSystem(ConnectorType.GITHUB)
                .name(repo.getFullName())
                .description(repo.getDescription())
                .status("active")
                .createdAt(toLocalDateTime(repo.getCreatedAt()))
                .modifiedAt(toLocalDateTime(repo.getUpdatedAt()))
                .build();
    }

    public ConnectorComment toConnectorComment(Map<String, Object> comment, String prExternalId) {
        if (comment == null) return null;

        String id = String.valueOf(comment.get("id"));
        String body = (String) comment.get("body");
        String createdAt = (String) comment.get("created_at");

        String authorId = null;
        if (comment.get("user") instanceof Map<?, ?> user) {
            authorId = String.valueOf(user.get("id"));
        }

        return ConnectorComment.builder()
                .externalId(id)
                .sourceSystem(ConnectorType.GITHUB)
                .taskExternalId(prExternalId)
                .authorExternalId(authorId)
                .content(body)
                .createdAt(createdAt != null ? OffsetDateTime.parse(createdAt).toLocalDateTime() : null)
                .build();
    }

    private LocalDateTime toLocalDateTime(OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) return null;
        return offsetDateTime.toLocalDateTime();
    }
}
