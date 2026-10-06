package com.sep490.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name = "vocabulary_entries", indexes = @Index(name = "idx_vocabulary_user_created", columnList = "user_id,created_at"))
@Getter @Setter @NoArgsConstructor
public class VocabularyJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "lesson_id") private LessonJpaEntity lesson;
    @Column(nullable = false, length = 200) private String word;
    @Column(nullable = false, length = 1000) private String meaning;
    @Column(length = 200) private String pronunciation;
    @Column(length = 2000) private String exampleSentence;
    @Column(length = 2000) private String note;
    @Column(nullable = false, updatable = false) private Instant createdAt;
}
