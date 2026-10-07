package com.sep490.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "vocabulary_examples",
        indexes = {
                @Index(
                        name = "idx_vocabulary_examples_meaning",
                        columnList = "vocabulary_meaning_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class VocabularyExampleJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "vocabulary_meaning_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_vocabulary_examples_meaning"
            )
    )
    private VocabularyMeaningJpaEntity vocabularyMeaning;

    @Column(name = "example_vi", nullable = false, columnDefinition = "TEXT")
    private String exampleVi;

    @Column(name = "translation_en", nullable = false, columnDefinition = "TEXT")
    private String translationEn;

    @Column(name = "audio_url", length = 2048)
    private String audioUrl;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}