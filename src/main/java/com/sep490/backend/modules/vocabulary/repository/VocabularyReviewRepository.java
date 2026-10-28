package com.sep490.backend.modules.vocabulary.repository;

import com.sep490.backend.modules.vocabulary.entity.VocabularyJpaEntity;
import com.sep490.backend.modules.vocabulary.entity.VocabularyReviewLogJpaEntity;
import com.sep490.backend.modules.vocabulary.entity.VocabularyReviewStateJpaEntity;
import com.sep490.backend.modules.vocabulary.repository.jpa.VocabularyJpaRepository;
import com.sep490.backend.modules.vocabulary.repository.jpa.VocabularyReviewLogJpaRepository;
import com.sep490.backend.modules.vocabulary.repository.jpa.VocabularyReviewStateJpaRepository;
import com.sep490.backend.modules.vocabulary.dto.model.VocabularyEntry;
import com.sep490.backend.modules.vocabulary.mapper.VocabularyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;

@Repository @RequiredArgsConstructor
public class VocabularyReviewRepository {
    private final VocabularyJpaRepository entries;
    private final VocabularyReviewStateJpaRepository states;
    private final VocabularyReviewLogJpaRepository logs;

    public List<VocabularyEntry> due(Long userId, Instant now, String sessionId) {
        return entries.findDue(userId, now, sessionId).stream().map(VocabularyMapper::toDomain).toList();
    }
    public Optional<VocabularyJpaEntity> lockOwned(Long id, Long userId) { return entries.lockOwned(id, userId); }
    public Optional<VocabularyReviewStateJpaEntity> state(Long entryId) { return states.findById(entryId); }
    public Optional<VocabularyReviewLogJpaEntity> result(Long entryId, String sessionId) {
        return logs.findByVocabularyEntry_IdAndSessionId(entryId, sessionId);
    }
    public List<VocabularyReviewLogJpaEntity> results(Long userId, String sessionId) {
        return logs.findByVocabularyEntry_User_IdAndSessionIdOrderByReviewedAtAscIdAsc(userId, sessionId);
    }
    public void save(VocabularyReviewStateJpaEntity state, VocabularyReviewLogJpaEntity log) {
        states.saveAndFlush(state);
        logs.saveAndFlush(log);
    }
}
