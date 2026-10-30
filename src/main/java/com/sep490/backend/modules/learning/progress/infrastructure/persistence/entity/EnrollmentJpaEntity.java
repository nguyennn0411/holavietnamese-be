package com.sep490.backend.modules.learning.progress.infrastructure.persistence.entity;
import com.sep490.backend.modules.learning.course.infrastructure.persistence.entity.CourseJpaEntity;
import com.sep490.backend.modules.learning.progress.domain.EnrollmentStatus;


import com.sep490.backend.modules.auth.entity.User;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
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
