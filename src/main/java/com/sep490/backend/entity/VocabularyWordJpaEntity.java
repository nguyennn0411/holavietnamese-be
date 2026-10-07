package com.sep490.backend.entity;

import com.sep490.backend.entity.enums.VocabularyStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.util.LinkedHashSet;
import java.util.Set;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
@Entity
@Table(
        name = "vocabularies",
        indexes = {
                @Index(
                        name = "idx_vocabularies_word",
                        columnList = "word"
                ),
                @Index(
                        name = "idx_vocabularies_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_vocabularies_level",
                        columnList = "cefr_level"
                ),
                @Index(
                        name = "idx_vocabularies_part_of_speech",
                        columnList = "part_of_speech"
                ),
                @Index(
                        name = "idx_vocabularies_filter",
                        columnList = "status, cefr_level, part_of_speech"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class VocabularyWordJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String word;

    @Column(length = 200)
    private String pronunciation;

    @Column(name = "part_of_speech", nullable = false, length = 40)
    private String partOfSpeech;

    @Column(name = "cefr_level", nullable = false, length = 10)
    private String cefrLevel;

    @Column(name = "audio_url", length = 2048)
    private String audioUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VocabularyStatus status = VocabularyStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "vocabulary_topic_mappings",
            joinColumns = @JoinColumn(
                    name = "vocabulary_id",
                    foreignKey = @ForeignKey(
                            name = "fk_vocabulary_topic_mapping_vocabulary"
                    )
            ),
            inverseJoinColumns = @JoinColumn(
                    name = "topic_id",
                    foreignKey = @ForeignKey(
                            name = "fk_vocabulary_topic_mapping_topic"
                    )
            )
    )
    private Set<VocabularyTopicJpaEntity> topics = new LinkedHashSet<>();

    @OneToMany(
            mappedBy = "vocabulary",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("displayOrder ASC, id ASC")
    private List<VocabularyMeaningJpaEntity> meanings = new ArrayList<>();
}