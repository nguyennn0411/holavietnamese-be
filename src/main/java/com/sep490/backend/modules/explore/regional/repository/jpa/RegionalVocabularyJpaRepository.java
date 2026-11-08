package com.sep490.backend.modules.explore.regional.repository.jpa;

import com.sep490.backend.modules.explore.regional.entity.RegionalVocabularyJpaEntity;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import org.springframework.data.jpa.repository.*;
import java.time.Instant;
import java.util.List;

public interface RegionalVocabularyJpaRepository extends JpaRepository<RegionalVocabularyJpaEntity, Long>,
        JpaSpecificationExecutor<RegionalVocabularyJpaEntity> {
    List<RegionalVocabularyJpaEntity> findByVocabularyIdAndRegion(Long vocabularyId, VietnamRegion region);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RegionalVocabularyJpaEntity variant
               set variant.status = ?2,
                   variant.updatedAt = ?3
             where variant.status = ?1
            """)
    int approveAllPending(VocabularyStatus pending, VocabularyStatus published, Instant updatedAt);
}
