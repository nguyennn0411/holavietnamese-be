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
    private final com.sep490.backend.learning.shared.ContentStore contentStore;
    public List<CourseResponse> availableCourses() {
        return courses.findAvailable().stream().map(this::response).toList();
    }
    public CourseResponse course(Long courseId) { return response(available(courseId)); }
    private Course available(Long id) {
        if(contentStore.count("SELECT count(*) FROM courses WHERE id=? AND status='PUBLISHED'",id)==0)
            throw LearningException.notFound("Available course");
        return courses.findById(id)
            .orElseThrow(() -> LearningException.notFound("Available course"));
    }
    private CourseResponse response(Course c) { return CourseResponse.from(c, courses.countPublishedLessons(c.id())); }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public EnrollmentResponse enroll(Long userId, Long courseId) {
        contentStore.one("SELECT id FROM users WHERE id=? FOR UPDATE",userId);
        Course c = available(courseId);
        Enrollment existing = enrollments.findForUpdate(userId, courseId).orElse(null);
        if (existing != null && existing.status() != EnrollmentStatus.CANCELLED && existing.status() != EnrollmentStatus.DROPPED)
            return toResponse(existing,c);
        Enrollment enrollment;
        if (existing != null) {
            boolean finished = new LearningProgress(courses.countPublishedLessons(courseId), enrollments.completedLessons(existing.id())).isComplete();
            enrollment = new Enrollment(existing.id(),existing.userId(),existing.courseId(),existing.enrolledAt(),
                finished ? EnrollmentStatus.COMPLETED : EnrollmentStatus.IN_PROGRESS,existing.lastAccessedLessonId(),existing.lastAccessedAt());
        } else {
            boolean modern=contentStore.count("SELECT count(*) FROM courses WHERE id=? AND slug IS NOT NULL",courseId)>0;
            enrollment = new Enrollment(null, userId, courseId, clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS), modern?EnrollmentStatus.NOT_STARTED:EnrollmentStatus.ACTIVE, null, null);
        }
        return toResponse(enrollments.save(enrollment), c);
    }
    public Optional<EnrollmentResponse> enrollmentStatus(Long userId, Long courseId) {
        Course c = available(courseId);
        return enrollments.find(userId, courseId).map(e -> toResponse(e, c));
    }
    private EnrollmentResponse toResponse(Enrollment e, Course c) {
        int total = courses.countPublishedLessons(c.id());
        int completed = enrollments.completedLessons(e.id());
        int percent = new LearningProgress(total, completed).percentage();
        return new EnrollmentResponse(e.id(), c.id(), c.title(), e.enrolledAt(), e.status(), percent);
    }
}
