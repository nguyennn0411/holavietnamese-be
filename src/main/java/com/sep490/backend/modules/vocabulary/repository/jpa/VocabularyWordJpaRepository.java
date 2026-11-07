package com.sep490.backend.modules.vocabulary.repository.jpa;

import com.sep490.backend.modules.vocabulary.entity.VocabularyWordJpaEntity;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VocabularyWordJpaRepository
        extends JpaRepository<VocabularyWordJpaEntity, Long>,
        JpaSpecificationExecutor<VocabularyWordJpaEntity> {
    java.util.List<VocabularyWordJpaEntity> findByWordIgnoreCaseAndPartOfSpeechIgnoreCase(String word,String partOfSpeech);
    // Some deployed MySQL collations ignore accents. Vietnamese headwords must retain them.
    default boolean existsByWordIgnoreCaseAndPartOfSpeechIgnoreCaseAndIdNot(String word,String partOfSpeech,Long id) {
        String normalized=com.sep490.backend.modules.vocabulary.service.VocabularyText.normalize(word);
        return findByWordIgnoreCaseAndPartOfSpeechIgnoreCase(word,partOfSpeech).stream()
                .anyMatch(candidate -> !candidate.getId().equals(id)
                        && com.sep490.backend.modules.vocabulary.service.VocabularyText.normalize(candidate.getWord()).equalsIgnoreCase(normalized));
    }
    java.util.List<VocabularyWordJpaEntity> findByWordIgnoreCaseAndStatusOrderByPartOfSpeechAscIdAsc(String word,com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update VocabularyWordJpaEntity word
               set word.status = :published,
                   word.publishedAt = :publishedAt,
                   word.updatedAt = :publishedAt
             where word.status = :pending
            """)
    int approveAllPending(
            @Param("pending") VocabularyStatus pending,
            @Param("published") VocabularyStatus published,
            @Param("publishedAt") Instant publishedAt
    );
}
