package com.sep490.backend.repository.jpa;

import com.sep490.backend.entity.RegionalVocabularyJpaEntity;
import com.sep490.backend.entity.enums.VietnamRegion;
import org.springframework.data.jpa.repository.*;

public interface RegionalVocabularyJpaRepository extends JpaRepository<RegionalVocabularyJpaEntity, Long>,
        JpaSpecificationExecutor<RegionalVocabularyJpaEntity> {
    boolean existsByVocabularyIdAndRegionAndWordIgnoreCaseAndIdNot(Long vocabularyId, VietnamRegion region, String word, Long id);
}
