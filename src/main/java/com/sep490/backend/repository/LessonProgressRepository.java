package com.sep490.backend.repository;

import java.util.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import com.sep490.backend.repository.LessonProgressRepository;
import com.sep490.backend.repository.jpa.LessonProgressJpaRepository;
import com.sep490.backend.entity.*;
import com.sep490.backend.mapper.LessonMapper;
import com.sep490.backend.dto.model.LessonProgress;
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
