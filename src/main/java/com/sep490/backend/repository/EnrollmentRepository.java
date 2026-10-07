package com.sep490.backend.repository;

import java.util.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import com.sep490.backend.repository.EnrollmentRepository;
import com.sep490.backend.repository.jpa.EnrollmentJpaRepository;
import com.sep490.backend.entity.*;
import com.sep490.backend.entity.enums.EnrollmentStatus;
import com.sep490.backend.mapper.EnrollmentMapper;
import com.sep490.backend.dto.model.Enrollment;
@Repository @RequiredArgsConstructor
public class EnrollmentRepository {
    private final EnrollmentJpaRepository repository;
    private final EntityManager em;
    public List<Enrollment> findByUser(Long userId) { return repository.findByUserIdOrderByEnrolledAtDesc(userId).stream().map(EnrollmentMapper::toDomain).toList(); }
    public Optional<Enrollment> find(Long user, Long course) { return repository.findByUserIdAndCourseId(user, course).map(EnrollmentMapper::toDomain); }
    public Optional<Enrollment> findForUpdate(Long user, Long course) { return repository.findForUpdate(user, course).map(EnrollmentMapper::toDomain); }
    public Enrollment save(Enrollment e) {
        EnrollmentJpaEntity row = e.id() == null ? new EnrollmentJpaEntity() : repository.findById(e.id()).orElseThrow();
        row.setUser(em.getReference(User.class, e.userId()));
        row.setCourse(em.getReference(CourseJpaEntity.class, e.courseId()));
        row.setEnrolledAt(e.enrolledAt());
        row.setStatus(row.getStatus() == EnrollmentStatus.COMPLETED && e.status() == EnrollmentStatus.ACTIVE
                ? EnrollmentStatus.COMPLETED
                : e.status());
        row.setLastAccessedLessonId(e.lastAccessedLessonId()); row.setLastAccessedAt(e.lastAccessedAt());
        return EnrollmentMapper.toDomain(repository.saveAndFlush(row));
    }
    public int completedLessons(Long id) { return repository.completedLessons(id); }
    public int completedLessonsByUser(Long userId) { return repository.completedLessonsByUserId(userId); }
    public void resetLessonProgress(Long id) { repository.resetLessonProgress(id); }
}
