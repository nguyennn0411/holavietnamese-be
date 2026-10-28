package com.sep490.backend.modules.vocabulary.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "vocabulary_review_states")
@Getter @Setter @NoArgsConstructor
public class VocabularyReviewStateJpaEntity {
    @Id @Column(name = "vocabulary_entry_id") private Long id;
    @MapsId @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vocabulary_entry_id") private VocabularyJpaEntity vocabularyEntry;
    @Column(nullable = false) private int repetitionCount;
    @Column(nullable = false) private long intervalMinutes;
    @Column(nullable = false, precision = 4, scale = 2) private BigDecimal easeFactor = new BigDecimal("2.50");
    @Column(nullable = false) private int lapseCount;
    private Instant lastReviewedAt;
    @Column(nullable = false) private Instant nextReviewAt;
    @Column(nullable = false) private Instant updatedAt;
}
