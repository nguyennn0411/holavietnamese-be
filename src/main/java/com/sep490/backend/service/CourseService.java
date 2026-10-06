package com.sep490.backend.service;

import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import com.sep490.backend.service.CourseService;
import com.sep490.backend.repository.*;
import com.sep490.backend.dto.response.*;
import com.sep490.backend.dto.model.*;
import com.sep490.backend.exception.LearningException;
import com.sep490.backend.entity.enums.*;
import static com.sep490.backend.exception.LearningException.Kind.*;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final Clock clock;
    public List<CourseResponse> availableCourses() {
        return courses.findAvailable().stream().map(this::response).toList();
    }
    public CourseResponse course(Long courseId) { return response(available(courseId)); }
    private Course available(Long id) {
        return courses.findById(id).filter(c -> c.status() == CourseStatus.PUBLISHED)
            .orElseThrow(() -> LearningException.notFound("Available course"));
    }
    private CourseResponse response(Course c) { return CourseResponse.from(c, courses.countPublishedLessons(c.id())); }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public EnrollmentResponse enroll(Integer userId, Long courseId) {
        Course c = available(courseId);
        Enrollment existing = enrollments.findForUpdate(userId, courseId).orElse(null);
        if (existing != null && existing.status() != EnrollmentStatus.CANCELLED)
            throw new LearningException(CONFLICT, "You are already enrolled in this course.");
        Enrollment enrollment;
        if (existing != null) {
            enrollments.resetLessonProgress(existing.id());
            enrollment = existing.resume(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        } else {
            enrollment = new Enrollment(null, userId, courseId, clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS), EnrollmentStatus.ACTIVE, null, null);
        }
        return toResponse(enrollments.save(enrollment), c);
    }
    public EnrollmentResponse enroll(Long userId, Long courseId) {
        return enroll(Math.toIntExact(userId), courseId);
    }
    public Optional<EnrollmentResponse> enrollmentStatus(Integer userId, Long courseId) {
        Course c = available(courseId);
        return enrollments.find(userId, courseId).map(e -> toResponse(e, c));
    }
    public Optional<EnrollmentResponse> enrollmentStatus(Long userId, Long courseId) {
        return enrollmentStatus(Math.toIntExact(userId), courseId);
    }
    private EnrollmentResponse toResponse(Enrollment e, Course c) {
        int total = courses.countPublishedLessons(c.id());
        int completed = enrollments.completedLessons(e.id());
        int percent = new LearningProgress(total, completed).percentage();
        return new EnrollmentResponse(e.id(), c.id(), c.title(), e.enrolledAt(), e.status(), percent);
    }
}
