package com.signalspoc.api.controller;

import com.signalspoc.api.dto.response.CommentResponse;
import com.signalspoc.domain.entity.Comment;
import com.signalspoc.domain.service.CommentService;
import com.signalspoc.shared.model.Enums.ConnectorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Comment read operations")
public class CommentController {

    private final CommentService commentService;

    @GetMapping
    @Operation(summary = "Get all comments")
    public ResponseEntity<Page<CommentResponse>> getComments(
            @PageableDefault(size = 20) Pageable pageable) {

        Page<Comment> comments = commentService.findAll(pageable);
        Page<CommentResponse> response = comments.map(CommentResponse::from);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get comment by internal ID")
    public ResponseEntity<CommentResponse> getCommentById(@PathVariable Long id) {
        Comment comment = commentService.findById(id);
        return ResponseEntity.ok(CommentResponse.from(comment));
    }

    @GetMapping("/{connector}/{externalId}")
    @Operation(summary = "Get comment by connector and external ID")
    public ResponseEntity<CommentResponse> getCommentByExternalId(
            @PathVariable String connector,
            @PathVariable String externalId) {

        ConnectorType type = ConnectorType.valueOf(connector.toUpperCase());
        return commentService.findByExternalId(externalId, type)
                .map(comment -> ResponseEntity.ok(CommentResponse.from(comment)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/task/{taskId}")
    @Operation(summary = "Get comments by task ID")
    public ResponseEntity<Page<CommentResponse>> getCommentsByTask(
            @PathVariable Long taskId,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<Comment> comments = commentService.findByTaskId(taskId, pageable);
        Page<CommentResponse> response = comments.map(CommentResponse::from);
        return ResponseEntity.ok(response);
    }
}
