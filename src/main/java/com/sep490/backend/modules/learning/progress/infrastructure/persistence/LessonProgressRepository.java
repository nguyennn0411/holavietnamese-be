package com.sep490.backend.modules.learning.progress.infrastructure.persistence;
import com.sep490.backend.modules.learning.lesson.infrastructure.persistence.entity.LessonJpaEntity;
import com.sep490.backend.modules.learning.lesson.infrastructure.persistence.LessonMapper;
import com.sep490.backend.modules.learning.progress.domain.model.LessonProgress;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.entity.EnrollmentJpaEntity;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.entity.LessonProgressJpaEntity;
import com.sep490.backend.modules.learning.progress.infrastructure.persistence.LessonProgressJpaRepository;



import java.util.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
@Repository @RequiredArgsConstructor
public class LessonProgressRepository {
    private final LessonProgressJpaRepository repository;
    private final EntityManager em;
    public Optional<LessonProgress> find(Long enrollment, Long lesson) { return repository.findByEnrollmentIdAndLessonId(enrollment, lesson).map(LessonMapper::toDomain); }
    public List<LessonProgress> findByEnrollment(Long id) { return repository.findByEnrollmentId(id).stream().map(LessonMapper::toDomain).toList(); }
    public LessonProgress save(LessonProgress p) {
        var row = p.id() == null ? new LessonProgressJpaEntity() : repository.findById(p.id()).orElseThrow();
        row.setEnrollment(em.getReference(EnrollmentJpaEntity.class, p.enrollmentId()));
        row.setLesson(em.getReference(LessonJpaEntity.class, p.lessonId()));
        row.setStatus(p.status()); row.setStartedAt(p.startedAt()); row.setCompletedAt(p.completedAt());
        return LessonMapper.toDomain(repository.saveAndFlush(row));
    }
}
