package com.sep490.backend.service;

import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import com.sep490.backend.repository.*;
import com.sep490.backend.dto.model.*;
import com.sep490.backend.exception.LearningException;
import com.sep490.backend.entity.enums.*;
import static com.sep490.backend.exception.LearningException.Kind.*;
@Component @RequiredArgsConstructor
public class LearnerAccess {
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    public Enrollment requireEnrollment(Long userId, Long courseId, boolean lock) {
        var course = courses.findById(courseId).filter(c -> c.status() == CourseStatus.PUBLISHED || c.status() == CourseStatus.ARCHIVED)
            .orElseThrow(() -> LearningException.notFound("Available course"));
        return (lock ? enrollments.findForUpdate(userId, course.id()) : enrollments.find(userId, course.id()))
            .filter(e -> e.status() != EnrollmentStatus.CANCELLED && e.status() != EnrollmentStatus.DROPPED)
            .orElseThrow(() -> new LearningException(FORBIDDEN, "Enroll in this course to access its lessons."));
    }
}
