package com.sep490.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "content_import_jobs") @Getter @Setter @NoArgsConstructor
public class ContentImportJobJpaEntity {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, length = 40) private String module;
    @Column(nullable = false, length = 255) private String filename;
    @Column(name = "actor_id", nullable = false) private Long actorId;
    @Column(name = "actor_name", nullable = false, length = 254) private String actorName;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "total_rows", nullable = false) private int totalRows;
    @Column(name = "valid_rows", nullable = false) private int validRows;
    @Column(name = "error_rows", nullable = false) private int errorRows;
    @Column(name = "imported_rows", nullable = false) private int importedRows;
    @Column(nullable = false, columnDefinition = "LONGTEXT") private String payload;
    @Column(nullable = false, columnDefinition = "LONGTEXT") private String diagnostics;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;
}
