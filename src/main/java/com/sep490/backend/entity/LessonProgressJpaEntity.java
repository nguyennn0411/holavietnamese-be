package com.sep490.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import com.sep490.backend.entity.enums.LessonStatus;
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
