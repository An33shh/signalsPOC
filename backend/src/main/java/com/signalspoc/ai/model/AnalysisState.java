package com.signalspoc.ai.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 500)
    private String entityId;

    @Column(name = "source_system", length = 50)
    private String sourceSystem;

    @Column(name = "content_checksum", length = 64)
    private String contentChecksum;

    @Column(name = "last_analyzed_at")
    private LocalDateTime lastAnalyzedAt;
}
