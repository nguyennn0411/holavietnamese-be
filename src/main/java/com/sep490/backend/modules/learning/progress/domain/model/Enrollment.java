package com.sep490.backend.modules.learning.progress.domain.model;
import com.sep490.backend.modules.learning.progress.domain.EnrollmentStatus;


import java.time.Instant;
public record Enrollment(Long id, Long userId, Long courseId, Instant enrolledAt, EnrollmentStatus status,
                         Long lastAccessedLessonId, Instant lastAccessedAt) {
    public Enrollment resume(Instant now) {
        return new Enrollment(id, userId, courseId, now, EnrollmentStatus.ACTIVE, null, null);
    }
}
