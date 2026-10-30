package com.sep490.backend.modules.explore.regional.entity;
import com.sep490.backend.modules.vocabulary.entity.VocabularyWordJpaEntity;

import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity @Table(name = "regional_vocabularies", uniqueConstraints =
        @UniqueConstraint(name = "uk_regional_vocabulary", columnNames = {"vocabulary_id", "region", "word"}))
@Getter @Setter @NoArgsConstructor
public class RegionalVocabularyJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vocabulary_id", nullable = false) private VocabularyWordJpaEntity vocabulary;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private VietnamRegion region;
    @Column(nullable = false, length = 200) private String word;
    @Column(nullable = false, length = 1000) private String meaning;
    @Column(length = 200) private String pronunciation;
    @Column(name = "audio_url", length = 2048) private String audioUrl;
    @Column(name = "example_sentence", length = 2000) private String exampleSentence;
    @Column(name = "usage_note", length = 2000) private String usageNote;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private VocabularyStatus status = VocabularyStatus.DRAFT;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
