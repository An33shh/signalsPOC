package com.signalspoc.api.dto.response;

import com.signalspoc.domain.entity.Comment;
import com.signalspoc.shared.model.Enums.ConnectorType;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class CommentResponse {

    private Long id;
    private String externalId;
    private ConnectorType sourceSystem;
    private TaskSummary task;
    private UserSummary author;
    private String content;
    private LocalDateTime externalCreatedAt;
    private LocalDateTime syncedAt;
    private LocalDateTime lastSyncedAt;

    @Data
    @Builder
    public static class TaskSummary {
        private Long id;
        private String title;
    }

    @Data
    @Builder
    public static class UserSummary {
        private Long id;
        private String name;
    }

    public static CommentResponse from(Comment comment) {
        TaskSummary taskSummary = TaskSummary.builder()
                .id(comment.getTask().getId())
                .title(comment.getTask().getTitle())
                .build();

        UserSummary authorSummary = null;
        if (comment.getAuthor() != null) {
            authorSummary = UserSummary.builder()
                    .id(comment.getAuthor().getId())
                    .name(comment.getAuthor().getName())
                    .build();
        }

        return CommentResponse.builder()
                .id(comment.getId())
                .externalId(comment.getExternalId())
                .sourceSystem(comment.getSourceSystem())
                .task(taskSummary)
                .author(authorSummary)
                .content(comment.getContent())
                .externalCreatedAt(comment.getExternalCreatedAt())
                .syncedAt(comment.getSyncedAt())
                .lastSyncedAt(comment.getLastSyncedAt())
                .build();
    }
}
