package com.sep490.backend.repository.jpa;

import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import com.sep490.backend.entity.EnrollmentJpaEntity;
public interface EnrollmentJpaRepository extends JpaRepository<EnrollmentJpaEntity, Long> {
    List<EnrollmentJpaEntity> findByUserIdOrderByEnrolledAtDesc(Integer userId);
    Optional<EnrollmentJpaEntity> findByUserIdAndCourseId(Integer userId, Long courseId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EnrollmentJpaEntity e where e.user.id = :userId and e.course.id = :courseId")
    Optional<EnrollmentJpaEntity> findForUpdate(Integer userId, Long courseId);
    @Query("select count(p) from LessonProgressJpaEntity p where p.enrollment.id = :enrollmentId and p.status = com.sep490.backend.entity.enums.LessonStatus.COMPLETED and p.lesson.published = true")
    int completedLessons(Long enrollmentId);
    @Query("select count(p) from LessonProgressJpaEntity p where p.enrollment.user.id = :userId and p.status = com.sep490.backend.entity.enums.LessonStatus.COMPLETED and p.lesson.published = true")
    int completedLessonsByUserId(Long userId);
    @Modifying
    @Query("delete from LessonProgressJpaEntity p where p.enrollment.id = :enrollmentId")
    void resetLessonProgress(Long enrollmentId);
}
