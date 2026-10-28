package com.sep490.backend.modules.learning.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sep490.backend.modules.learning.repository.CourseRepository;
import com.sep490.backend.modules.learning.repository.EnrollmentRepository;
import com.sep490.backend.modules.learning.dto.response.MyCourseResponse;
import com.sep490.backend.modules.learning.entity.enums.EnrollmentStatus;
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class MyCoursesService {
    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;
    public List<MyCourseResponse> myCourses(Long userId) {
        return enrollments.findByUser(userId).stream().filter(e -> e.status() != EnrollmentStatus.CANCELLED && e.status() != EnrollmentStatus.DROPPED).map(e -> {
            var c = courses.findById(e.courseId()).orElseThrow();
            int total = courses.countPublishedLessons(c.id());
            int completed = enrollments.completedLessons(e.id());
            int percentage = new com.sep490.backend.modules.learning.dto.model.LearningProgress(total, completed).percentage();
            return new MyCourseResponse(e.id(), c.id(), c.title(), c.thumbnailUrl(), c.level(), total, completed, percentage, e.status(), e.lastAccessedLessonId());
        }).toList();
    }
}
