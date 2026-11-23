package com.sep490.backend.modules.explore.repository;

import com.sep490.backend.modules.explore.entity.RegionalVocabulary;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

public interface RegionalVocabularyRepository
        extends JpaRepository<RegionalVocabulary, Long>,
                JpaSpecificationExecutor<RegionalVocabulary> {
    @Override
    @EntityGraph(attributePaths = "vocabulary")
    List<RegionalVocabulary> findAll(Specification<RegionalVocabulary> specification, Sort sort);

    @Override
    @EntityGraph(attributePaths = "vocabulary")
    Page<RegionalVocabulary> findAll(
            Specification<RegionalVocabulary> specification, Pageable pageable);

    /** Lay cac bien the cua mot tu chuan trong cung vung de kiem tra trung. */
    List<RegionalVocabulary> findByVocabularyIdAndRegion(Long vocabularyId, VietnamRegion region);

    @Query(
            value = """
            select master.id from RegionalVocabulary variant
            join variant.vocabulary master
            where variant.status = :published and master.status = :published
              and (:region is null or variant.region = :region)
              and (:keyword = '' or locate(lower(:keyword), lower(variant.word)) > 0
                   or locate(lower(:keyword), lower(variant.meaning)) > 0
                   or locate(lower(:keyword), lower(master.word)) > 0)
            group by master.id, master.word
            order by master.word, master.id
            """,
            countQuery = """
            select count(distinct master.id) from RegionalVocabulary variant
            join variant.vocabulary master
            where variant.status = :published and master.status = :published
              and (:region is null or variant.region = :region)
              and (:keyword = '' or locate(lower(:keyword), lower(variant.word)) > 0
                   or locate(lower(:keyword), lower(variant.meaning)) > 0
                   or locate(lower(:keyword), lower(master.word)) > 0)
            """)
    Page<Long> findPublishedMasterIds(
            String keyword,
            VietnamRegion region,
            VocabularyStatus published,
            Pageable pageable);

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
