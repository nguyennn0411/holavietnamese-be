package com.sep490.backend.mapper;

import com.sep490.backend.entity.EnrollmentJpaEntity;
import com.sep490.backend.dto.model.Enrollment;
public final class EnrollmentMapper {
    private EnrollmentMapper() {}
    public static Enrollment toDomain(EnrollmentJpaEntity e) { return new Enrollment(e.getId(), e.getUser().getId().longValue(), e.getCourse().getId(), e.getEnrolledAt(), e.getStatus(), e.getLastAccessedLessonId(), e.getLastAccessedAt()); }
}
