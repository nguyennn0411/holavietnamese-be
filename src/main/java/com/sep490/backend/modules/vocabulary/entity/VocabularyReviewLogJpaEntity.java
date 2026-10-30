package com.sep490.backend.modules.vocabulary.entity;

import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyReviewRating;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "vocabulary_review_logs")
@Getter @Setter @NoArgsConstructor
public class VocabularyReviewLogJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vocabulary_entry_id") private VocabularyJpaEntity vocabularyEntry;
    @Column(nullable = false, length = 36) private String sessionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private VocabularyReviewRating rating;
    @Column(nullable = false) private Instant reviewedAt;
    @Column(nullable = false) private Instant nextReviewAt;
    @Column(nullable = false) private long intervalMinutes;
}
