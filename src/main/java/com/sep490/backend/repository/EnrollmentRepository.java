package com.sep490.backend.repository;

import com.sep490.backend.dto.model.Enrollment;
import com.sep490.backend.entity.CourseJpaEntity;
import com.sep490.backend.entity.EnrollmentJpaEntity;
import com.sep490.backend.entity.User;
import com.sep490.backend.entity.enums.EnrollmentStatus;
import com.sep490.backend.mapper.EnrollmentMapper;
import com.sep490.backend.repository.jpa.EnrollmentJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EnrollmentRepository {
    private final EnrollmentJpaRepository repository;
    private final EntityManager em;

    public List<Enrollment> findByUser(Long userId) {
        return repository.findByUserIdOrderByEnrolledAtDesc(userId).stream().map(EnrollmentMapper::toDomain).toList();
    }

    public Optional<Enrollment> find(Long user, Long course) {
        return repository.findByUserIdAndCourseId(user, course).map(EnrollmentMapper::toDomain);
    }

    public Optional<Enrollment> findForUpdate(Long user, Long course) {
        return repository.findForUpdate(user, course).map(row -> {
            // Activity Engine writes through JDBC; refresh a previously managed JPA row under the lock.
            em.refresh(row, LockModeType.PESSIMISTIC_WRITE);
            return EnrollmentMapper.toDomain(row);
        });
    }

    public Enrollment save(Enrollment e) {
        EnrollmentJpaEntity row = e.id() == null ? new EnrollmentJpaEntity() : repository.findById(e.id()).orElseThrow();
        row.setUser(em.getReference(User.class, e.userId()));
        row.setCourse(em.getReference(CourseJpaEntity.class, e.courseId()));
        row.setEnrolledAt(e.enrolledAt());
        row.setStatus(row.getStatus() == EnrollmentStatus.COMPLETED && e.status() == EnrollmentStatus.ACTIVE
                ? EnrollmentStatus.COMPLETED
                : e.status());
        row.setLastAccessedLessonId(e.lastAccessedLessonId());
        row.setLastAccessedAt(e.lastAccessedAt());
        return EnrollmentMapper.toDomain(repository.saveAndFlush(row));
    }

    public int completedLessons(Long id) {
        return repository.completedLessons(id);
    }

    public int completedLessonsByUser(Long userId) {
        return repository.completedLessonsByUserId(userId);
    }

    @SuppressWarnings("unused")
    public void resetLessonProgress(Long id) {
        repository.resetLessonProgress(id);
    }
}
