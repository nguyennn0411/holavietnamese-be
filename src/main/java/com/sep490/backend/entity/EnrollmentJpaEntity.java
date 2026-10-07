package com.sep490.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import com.sep490.backend.entity.enums.EnrollmentStatus;
@Entity @Table(name = "enrollments", uniqueConstraints = @UniqueConstraint(name = "uk_enrollment_user_course", columnNames = {"user_id", "course_id"}))
@Getter @Setter @NoArgsConstructor
public class EnrollmentJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "course_id") private CourseJpaEntity course;
    @Column(nullable = false) private Instant enrolledAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private EnrollmentStatus status;
    private Long lastAccessedLessonId;
    private Instant lastAccessedAt;
}
