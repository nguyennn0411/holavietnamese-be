package com.sep490.backend.service;

import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sep490.backend.service.LearningProgressService;
import com.sep490.backend.repository.*;
import com.sep490.backend.dto.response.LearningProgressResponse;
import com.sep490.backend.dto.model.*;
import com.sep490.backend.entity.enums.EnrollmentStatus;
import com.sep490.backend.entity.enums.LessonStatus;
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class LearningProgressService {
    private final LearnerAccess access;
    private final LessonRepository lessons;
    private final LessonProgressRepository progress;
    public LearningProgressResponse progress(Integer userId, Long courseId) {
        var e = access.requireEnrollment(userId, courseId, false);
        var states = progress.findByEnrollment(e.id()).stream().collect(Collectors.toMap(LessonProgress::lessonId, Function.identity()));
        var list = lessons.findPublishedByCourse(courseId).stream().map(l -> {
            var p = states.get(l.id());
            return new LearningProgressResponse.LessonState(l.id(), p == null ? LessonStatus.NOT_STARTED : p.status(), p == null ? null : p.startedAt(), p == null ? null : p.completedAt());
        }).toList();
        var summary = new LearningProgress(list.size(), (int) list.stream().filter(l -> l.status() == LessonStatus.COMPLETED).count());
        var status = summary.isComplete() ? EnrollmentStatus.COMPLETED : e.status();
        return new LearningProgressResponse(courseId, summary.totalLessons(), summary.completedLessons(), summary.percentage(), e.lastAccessedLessonId(), e.lastAccessedAt(), status, list);
    }
}
