package com.sep490.backend.modules.explore.entity;

import com.sep490.backend.modules.vocabulary.entity.VocabularyWord;

import jakarta.persistence.*;

import lombok.*;

@Entity
@Table(
        name = "destination_vocabularies",
        uniqueConstraints = @UniqueConstraint(columnNames = {"destination_id", "vocabulary_id"}))
@Getter
@Setter
@NoArgsConstructor
public class DestinationVocabulary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long destinationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vocabulary_id")
    private VocabularyWord vocabulary;

    @Column(nullable = false)
    private int displayOrder;
}
