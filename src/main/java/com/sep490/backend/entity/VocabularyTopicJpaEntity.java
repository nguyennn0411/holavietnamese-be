package com.sep490.backend.entity;

import com.sep490.backend.entity.enums.VocabularyStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "vocabulary_topics",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_vocabulary_topics_slug",
                        columnNames = "slug"
                )
        },
        indexes = {
                @Index(
                        name = "idx_vocabulary_topics_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_vocabulary_topics_display_order",
                        columnList = "display_order"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class VocabularyTopicJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 120)
    private String slug;

    @Column(length = 500)
    private String description;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VocabularyStatus status = VocabularyStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}