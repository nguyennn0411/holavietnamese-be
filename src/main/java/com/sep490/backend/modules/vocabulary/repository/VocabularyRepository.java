package com.sep490.backend.modules.vocabulary.repository;

import java.util.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import com.sep490.backend.modules.vocabulary.repository.jpa.VocabularyJpaRepository;
import com.sep490.backend.modules.auth.entity.User;
import com.sep490.backend.modules.learning.entity.LessonJpaEntity;
import com.sep490.backend.modules.learning.port.VocabularyProgressReader;
import com.sep490.backend.modules.vocabulary.entity.VocabularyJpaEntity;
import com.sep490.backend.modules.vocabulary.entity.VocabularyWordJpaEntity;
import com.sep490.backend.modules.vocabulary.mapper.VocabularyMapper;
import com.sep490.backend.modules.vocabulary.dto.model.VocabularyEntry;
@Repository @RequiredArgsConstructor
public class VocabularyRepository implements VocabularyProgressReader {
    private final VocabularyJpaRepository repository;
    private final EntityManager em;
    public List<VocabularyEntry> search(Long userId, String search, Long courseId, Long lessonId) { return repository.search(userId, search, courseId, lessonId).stream().map(VocabularyMapper::toDomain).toList(); }
    public Optional<VocabularyEntry> findOwned(Long id, Long userId) { return repository.findByIdAndUserId(id, userId).map(VocabularyMapper::toDomain); }
    @Override
    public int countByUser(Long userId) { return repository.countByUserId(userId); }


    public Optional<VocabularyEntry> findOwnedByMasterVocabulary(
            Long userId,
            Long masterVocabularyId
    ) {
        return repository.findByUser_IdAndMasterVocabulary_Id(
                userId,
                masterVocabularyId
        ).map(VocabularyMapper::toDomain);
    }


    public VocabularyEntry save(VocabularyEntry e) {
        var row = e.id() == null
                ? new VocabularyJpaEntity()
                : repository.findByIdAndUserId(e.id(), e.userId()).orElseThrow();

        row.setUser(em.getReference(User.class, e.userId()));
        row.setLesson(e.lessonId() == null ? null : em.getReference(LessonJpaEntity.class, e.lessonId()));

        if (e.masterVocabularyId() != null) {
            row.setMasterVocabulary(
                    em.getReference(
                            VocabularyWordJpaEntity.class,
                            e.masterVocabularyId()
                    )
            );
        }

        row.setWord(e.word()); row.setMeaning(e.meaning()); row.setPronunciation(e.pronunciation());
        row.setExampleSentence(e.exampleSentence()); row.setNote(e.note()); row.setCreatedAt(e.createdAt());

        return VocabularyMapper.toDomain(repository.saveAndFlush(row));
    }

    public void delete(VocabularyEntry e) { repository.deleteOwned(e.id(), e.userId()); }
}
