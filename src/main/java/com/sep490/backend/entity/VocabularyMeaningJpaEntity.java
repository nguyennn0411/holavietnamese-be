package com.sep490.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "vocabulary_meanings",
        indexes = {
                @Index(
                        name = "idx_vocabulary_meanings_vocabulary",
                        columnList = "vocabulary_id"
                ),
                @Index(
                        name = "idx_vocabulary_meanings_translation",
                        columnList = "translation_en"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class VocabularyMeaningJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "vocabulary_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_vocabulary_meanings_vocabulary"
            )
    )
    private VocabularyWordJpaEntity vocabulary;

    @Column(name = "translation_en", nullable = false, length = 500)
    private String translationEn;

    @Column(name = "definition_en", columnDefinition = "TEXT")
    private String definitionEn;

    @Column(name = "usage_note", columnDefinition = "TEXT")
    private String usageNote;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(
            mappedBy = "vocabularyMeaning",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("displayOrder ASC, id ASC")
    private List<VocabularyExampleJpaEntity> examples = new ArrayList<>();
}