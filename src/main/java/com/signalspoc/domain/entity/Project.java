package com.signalspoc.domain.entity;

import com.signalspoc.shared.model.Enums.ConnectorType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "projects",
        uniqueConstraints = @UniqueConstraint(columnNames = {"external_id", "source_system"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"externalId", "sourceSystem"})
@ToString(exclude = {"tasks", "owner"})
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 255)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_system", nullable = false, length = 50)
    private ConnectorType sourceSystem;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Task> tasks = new HashSet<>();

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
