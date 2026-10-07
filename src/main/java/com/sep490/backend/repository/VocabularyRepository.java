package com.sep490.backend.repository;

import java.util.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import com.sep490.backend.repository.VocabularyRepository;
import com.sep490.backend.repository.jpa.VocabularyJpaRepository;
import com.sep490.backend.entity.*;
import com.sep490.backend.mapper.VocabularyMapper;
import com.sep490.backend.dto.model.VocabularyEntry;
@Repository @RequiredArgsConstructor
public class VocabularyRepository {
    private final VocabularyJpaRepository repository;
    private final EntityManager em;
    public List<VocabularyEntry> search(Long userId, String search, Long courseId, Long lessonId) { return repository.search(userId, search, courseId, lessonId).stream().map(VocabularyMapper::toDomain).toList(); }
    public Optional<VocabularyEntry> findOwned(Long id, Long userId) { return repository.findByIdAndUserId(id, userId).map(VocabularyMapper::toDomain); }
    public int countByUser(Long userId) { return repository.countByUserId(userId); }
    public VocabularyEntry save(VocabularyEntry e) {
        var row = e.id() == null ? new VocabularyJpaEntity() : repository.findByIdAndUserId(e.id(), e.userId()).orElseThrow();
        row.setUser(em.getReference(User.class, e.userId()));
        row.setLesson(e.lessonId() == null ? null : em.getReference(LessonJpaEntity.class, e.lessonId()));
        row.setWord(e.word()); row.setMeaning(e.meaning()); row.setPronunciation(e.pronunciation());
        row.setExampleSentence(e.exampleSentence()); row.setNote(e.note()); row.setCreatedAt(e.createdAt());
        return VocabularyMapper.toDomain(repository.saveAndFlush(row));
    }
    public void delete(VocabularyEntry e) { repository.deleteById(e.id()); }
}
