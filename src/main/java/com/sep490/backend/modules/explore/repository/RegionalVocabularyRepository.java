package com.sep490.backend.modules.explore.repository;

import com.sep490.backend.modules.explore.entity.RegionalVocabulary;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;

import org.springframework.data.jpa.repository.*;

import java.time.Instant;
import java.util.List;

public interface RegionalVocabularyRepository
        extends JpaRepository<RegionalVocabulary, Long>,
                JpaSpecificationExecutor<RegionalVocabulary> {
    /** Lay cac bien the cua mot tu chuan trong cung vung de kiem tra trung. */
    List<RegionalVocabulary> findByVocabularyIdAndRegion(Long vocabularyId, VietnamRegion region);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update RegionalVocabulary variant
               set variant.status = ?2,
                   variant.updatedAt = ?3
             where variant.status = ?1
            """)
    /** Phe duyet cac noi dung dang cho duyet trong cung mot thao tac. */
    int approveAllPending(VocabularyStatus pending, VocabularyStatus published, Instant updatedAt);
}
