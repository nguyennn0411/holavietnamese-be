package com.sep490.backend.repository.jpa;

import com.sep490.backend.entity.EnrollmentJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EnrollmentJpaRepository extends JpaRepository<EnrollmentJpaEntity, Long> {

    List<EnrollmentJpaEntity> findByUserIdOrderByEnrolledAtDesc(Long userId);

    Optional<EnrollmentJpaEntity> findByUserIdAndCourseId(Long userId, Long courseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EnrollmentJpaEntity e where e.user.id = :userId and e.course.id = :courseId")
    Optional<EnrollmentJpaEntity> findForUpdate(Long userId, Long courseId);

    @Query("select count(p) from LessonProgressJpaEntity p where p.enrollment.id = :enrollmentId and p.status = com.sep490.backend.entity.enums.LessonStatus.COMPLETED and p.lesson.published = true")
    int completedLessons(Long enrollmentId);

    @Query("select count(p) from LessonProgressJpaEntity p where p.enrollment.user.id = :userId and p.status = com.sep490.backend.entity.enums.LessonStatus.COMPLETED and p.lesson.published = true")
    int completedLessonsByUserId(Long userId);

    @Modifying
    @Query("delete from LessonProgressJpaEntity p where p.enrollment.id = :enrollmentId")
    void resetLessonProgress(Long enrollmentId);
}
