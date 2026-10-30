package com.sep490.backend.modules.learning.progress.infrastructure.persistence;
import com.sep490.backend.modules.learning.progress.domain.model.Enrollment;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.entity.EnrollmentJpaEntity;


public final class EnrollmentMapper {
    private EnrollmentMapper() {}
    public static Enrollment toDomain(EnrollmentJpaEntity e) { return new Enrollment(e.getId(), e.getUser().getId().longValue(), e.getCourse().getId(), e.getEnrolledAt(), e.getStatus(), e.getLastAccessedLessonId(), e.getLastAccessedAt()); }
}
