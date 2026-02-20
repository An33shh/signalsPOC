package com.signalspoc.domain.service;

import com.signalspoc.connector.model.ConnectorComment;
import com.signalspoc.domain.entity.Comment;
import com.signalspoc.domain.entity.Task;
import com.signalspoc.domain.entity.User;
import com.signalspoc.domain.repository.CommentRepository;
import com.signalspoc.domain.repository.TaskRepository;
import com.signalspoc.domain.repository.UserRepository;
import com.signalspoc.shared.exception.Exceptions.ResourceNotFoundException;
import com.signalspoc.shared.model.Enums.ConnectorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

    private final CommentRepository commentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Comment findById(Long id) {
        return commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", id));
    }

    @Transactional(readOnly = true)
    public Optional<Comment> findByExternalId(String externalId, ConnectorType sourceSystem) {
        return commentRepository.findByExternalIdAndSourceSystem(externalId, sourceSystem);
    }

    @Transactional(readOnly = true)
    public Page<Comment> findAll(Pageable pageable) {
        return commentRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Comment> findByTaskId(Long taskId, Pageable pageable) {
        return commentRepository.findByTaskId(taskId, pageable);
    }

    @Transactional
    public UserService.UpsertResult<Comment> upsert(ConnectorComment connectorComment) {
        Task task = resolveTask(connectorComment.getTaskExternalId(), connectorComment.getSourceSystem());
        if (task == null) {
            log.warn("Task not found for comment: {} (task: {})",
                    connectorComment.getExternalId(), connectorComment.getTaskExternalId());
            return null;
        }

        User author = resolveUser(connectorComment.getAuthorExternalId(), connectorComment.getSourceSystem());

        Optional<Comment> existing = commentRepository.findByExternalIdAndSourceSystem(
                connectorComment.getExternalId(),
                connectorComment.getSourceSystem());

        if (existing.isPresent()) {
            Comment comment = existing.get();
            updateCommentFields(comment, connectorComment, task, author);
            Comment saved = commentRepository.save(comment);
            log.debug("Updated comment: {}", saved.getExternalId());
            return new UserService.UpsertResult<>(saved, false);
        } else {
            Comment comment = createComment(connectorComment, task, author);
            Comment saved = commentRepository.save(comment);
            log.debug("Created comment: {}", saved.getExternalId());
            return new UserService.UpsertResult<>(saved, true);
        }
    }

    @Transactional
    public List<UserService.UpsertResult<Comment>> upsertAll(List<ConnectorComment> connectorComments) {
        return connectorComments.stream()
                .map(this::upsert)
                .filter(result -> result != null)
                .toList();
    }

    private Task resolveTask(String taskExternalId, ConnectorType sourceSystem) {
        if (taskExternalId == null) {
            return null;
        }
        return taskRepository.findByExternalIdAndSourceSystem(taskExternalId, sourceSystem)
                .orElse(null);
    }

    private User resolveUser(String userExternalId, ConnectorType sourceSystem) {
        if (userExternalId == null) {
            return null;
        }
        return userRepository.findByExternalIdAndSourceSystem(userExternalId, sourceSystem)
                .orElse(null);
    }

    private Comment createComment(ConnectorComment connectorComment, Task task, User author) {
        return Comment.builder()
                .externalId(connectorComment.getExternalId())
                .sourceSystem(connectorComment.getSourceSystem())
                .task(task)
                .author(author)
                .content(connectorComment.getContent())
                .externalCreatedAt(connectorComment.getCreatedAt())
                .build();
    }

    private void updateCommentFields(Comment comment, ConnectorComment connectorComment, Task task, User author) {
        comment.setTask(task);
        comment.setAuthor(author);
        comment.setContent(connectorComment.getContent());
    }
}
