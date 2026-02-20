package com.signalspoc.domain.entity;

import com.signalspoc.shared.model.Enums.ConnectorType;
import com.signalspoc.shared.model.Enums.Priority;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "tasks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"external_id", "source_system"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"externalId", "sourceSystem"})
@ToString(exclude = {"project", "assignee", "comments"})
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 255)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_system", nullable = false, length = 50)
    private ConnectorType sourceSystem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 1000)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Priority priority;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Comment> comments = new HashSet<>();

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "external_created_at")
    private LocalDateTime externalCreatedAt;

    @Column(name = "external_modified_at")
    private LocalDateTime externalModifiedAt;

    @CreationTimestamp
    @Column(name = "synced_at", nullable = false, updatable = false)
    private LocalDateTime syncedAt;

    @UpdateTimestamp
    @Column(name = "last_synced_at", nullable = false)
    private LocalDateTime lastSyncedAt;
}
