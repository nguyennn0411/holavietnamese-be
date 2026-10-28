package com.sep490.backend.modules.culture.repository.jpa;

import com.sep490.backend.modules.culture.entity.RegionalVocabularyJpaEntity;
import com.sep490.backend.modules.culture.entity.enums.VietnamRegion;
import org.springframework.data.jpa.repository.*;

public interface RegionalVocabularyJpaRepository extends JpaRepository<RegionalVocabularyJpaEntity, Long>,
        JpaSpecificationExecutor<RegionalVocabularyJpaEntity> {
    boolean existsByVocabularyIdAndRegionAndWordIgnoreCaseAndIdNot(Long vocabularyId, VietnamRegion region, String word, Long id);
}
