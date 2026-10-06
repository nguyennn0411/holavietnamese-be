package com.sep490.backend.service;

import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sep490.backend.service.*;
import com.sep490.backend.repository.*;
import com.sep490.backend.dto.response.LessonResponse;
import com.sep490.backend.dto.model.*;
import com.sep490.backend.exception.LearningException;
import com.sep490.backend.entity.enums.*;
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class LessonService {
    private final LessonRepository lessons;
    private final LessonProgressRepository progress;
    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;
    private final LearnerAccess access;
    private final Clock clock;
    public List<LessonResponse> lessons(Integer userId, Long courseId) {
        Enrollment e = access.requireEnrollment(userId, courseId, false);
        List<Lesson> list = lessons.findPublishedByCourse(courseId);
        var states = progress.findByEnrollment(e.id()).stream().collect(Collectors.toMap(LessonProgress::lessonId, Function.identity()));
        List<LessonResponse> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) result.add(response(list.get(i), states.get(list.get(i).id()), list, i, false));
        return result;
    }
    public LessonResponse lesson(Integer userId, Long lessonId) {
        Lesson l = published(lessonId);
        Enrollment e = access.requireEnrollment(userId, l.courseId(), false);
        return detail(l, progress.find(e.id(), l.id()).orElse(null));
    }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public LessonResponse start(Integer userId, Long lessonId) { return update(userId, lessonId, false); }
    public LessonResponse start(Long userId, Long lessonId) { return start(Math.toIntExact(userId), lessonId); }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public LessonResponse complete(Integer userId, Long lessonId) { return update(userId, lessonId, true); }
    public LessonResponse complete(Long userId, Long lessonId) { return complete(Math.toIntExact(userId), lessonId); }
    private LessonResponse update(Integer userId, Long lessonId, boolean complete) {
        Lesson l = published(lessonId);
        // Every progress mutation locks the parent enrollment, serializing concurrent lesson completions.
        Enrollment e = access.requireEnrollment(userId, l.courseId(), true);
        var now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        LessonProgress p = progress.find(e.id(), l.id()).orElseGet(() -> LessonProgress.start(e.id(), l.id(), now));
        if (complete) p = p.complete(now);
        p = progress.save(p);
        int total = courses.countPublishedLessons(l.courseId());
        int completed = enrollments.completedLessons(e.id());
        EnrollmentStatus status = new LearningProgress(total, completed).isComplete() ? EnrollmentStatus.COMPLETED : EnrollmentStatus.ACTIVE;
        enrollments.save(new Enrollment(e.id(), e.userId(), e.courseId(), e.enrolledAt(), status, l.id(), now));
        return detail(l, p);
    }
    private Lesson published(Long id) {
        return lessons.findById(id).filter(Lesson::published).orElseThrow(() -> LearningException.notFound("Lesson"));
    }
    private LessonResponse detail(Lesson lesson, LessonProgress p) {
        List<Lesson> list = lessons.findPublishedByCourse(lesson.courseId());
        int index = -1;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(lesson.id())) { index = i; break; }
        return response(lesson, p, list, index, true);
    }
    private LessonResponse response(Lesson l, LessonProgress p, List<Lesson> list, int i, boolean includeContent) {
        return new LessonResponse(l.id(), l.courseId(), l.title(), l.description(), l.lessonOrder(),
            includeContent ? l.content() : null, includeContent ? l.videoUrl() : null, includeContent ? l.audioUrl() : null,
            l.estimatedDuration(), p == null ? LessonStatus.NOT_STARTED : p.status(),
            i > 0 ? list.get(i-1).id() : null, i >= 0 && i+1 < list.size() ? list.get(i+1).id() : null,
            p == null ? null : p.startedAt(), p == null ? null : p.completedAt());
    }
}
