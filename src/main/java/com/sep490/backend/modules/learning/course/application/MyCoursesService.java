package com.sep490.backend.modules.learning.course.application;
import com.sep490.backend.modules.learning.course.infrastructure.persistence.CourseRepository;
import com.sep490.backend.modules.learning.course.web.dto.MyCourseResponse;
import com.sep490.backend.modules.learning.progress.domain.EnrollmentStatus;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.EnrollmentRepository;



import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class MyCoursesService {
    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;
    public List<MyCourseResponse> myCourses(Long userId) {
        return enrollments.findByUser(userId).stream().filter(e -> e.status() != EnrollmentStatus.CANCELLED && e.status() != EnrollmentStatus.DROPPED).map(e -> {
            var c = courses.findById(e.courseId()).orElseThrow();
            int total = courses.countPublishedLessons(c.id());
            int completed = enrollments.completedLessons(e.id());
            int percentage = new com.sep490.backend.modules.learning.progress.domain.model.LearningProgress(total, completed).percentage();
            return new MyCourseResponse(e.id(), c.id(), c.title(), c.thumbnailUrl(), c.level(), total, completed, percentage, e.status(), e.lastAccessedLessonId());
        }).toList();
    }
}
