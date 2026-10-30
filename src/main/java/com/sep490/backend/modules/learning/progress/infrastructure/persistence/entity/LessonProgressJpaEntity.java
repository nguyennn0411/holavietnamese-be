package com.sep490.backend.modules.learning.progress.infrastructure.persistence.entity;
import com.sep490.backend.modules.learning.lesson.domain.LessonStatus;
import com.sep490.backend.modules.learning.lesson.infrastructure.persistence.entity.LessonJpaEntity;



import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name = "lesson_progress", uniqueConstraints = @UniqueConstraint(name = "uk_progress_enrollment_lesson", columnNames = {"enrollment_id", "lesson_id"}))
@Getter @Setter @NoArgsConstructor
public class LessonProgressJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "enrollment_id") private EnrollmentJpaEntity enrollment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "lesson_id") private LessonJpaEntity lesson;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private LessonStatus status;
    private Instant startedAt;
    private Instant completedAt;
}
