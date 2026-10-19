package com.sep490.backend.repository.jpa;

import com.sep490.backend.entity.VocabularyWordJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface VocabularyWordJpaRepository
        extends JpaRepository<VocabularyWordJpaEntity, Long>,
        JpaSpecificationExecutor<VocabularyWordJpaEntity> {
    java.util.List<VocabularyWordJpaEntity> findByWordIgnoreCaseAndPartOfSpeechIgnoreCase(String word,String partOfSpeech);
    boolean existsBySourceMetadataContaining(String sourceFingerprint);
    // Some deployed MySQL collations ignore accents. Vietnamese headwords must retain them.
    default boolean existsByWordIgnoreCaseAndPartOfSpeechIgnoreCaseAndIdNot(String word,String partOfSpeech,Long id) {
        String normalized=com.sep490.backend.service.VocabularyText.normalize(word);
        return findByWordIgnoreCaseAndPartOfSpeechIgnoreCase(word,partOfSpeech).stream()
                .anyMatch(candidate -> !candidate.getId().equals(id)
                        && com.sep490.backend.service.VocabularyText.normalize(candidate.getWord()).equalsIgnoreCase(normalized));
    }
    java.util.List<VocabularyWordJpaEntity> findByWordIgnoreCaseAndStatusOrderByPartOfSpeechAscIdAsc(String word,com.sep490.backend.entity.enums.VocabularyStatus status);
}
