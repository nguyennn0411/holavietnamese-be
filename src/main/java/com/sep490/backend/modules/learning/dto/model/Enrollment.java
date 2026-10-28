package com.sep490.backend.modules.learning.dto.model;

import java.time.Instant;
import com.sep490.backend.modules.learning.entity.enums.EnrollmentStatus;
public record Enrollment(Long id, Long userId, Long courseId, Instant enrolledAt, EnrollmentStatus status,
                         Long lastAccessedLessonId, Instant lastAccessedAt) {
    public Enrollment resume(Instant now) {
        return new Enrollment(id, userId, courseId, now, EnrollmentStatus.ACTIVE, null, null);
    }
}
