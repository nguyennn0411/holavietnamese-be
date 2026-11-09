package com.sep490.backend.modules.vocabulary.repository;

import com.sep490.backend.modules.vocabulary.entity.VocabularyMeaning;
import com.sep490.backend.modules.vocabulary.entity.VocabularyWord;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.modules.vocabulary.service.VocabularyText;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public interface VocabularyRepository
        extends JpaRepository<VocabularyWord, Long>, JpaSpecificationExecutor<VocabularyWord> {
    java.util.List<VocabularyWord> findByWordIgnoreCaseAndPartOfSpeechIgnoreCase(
            String word, String partOfSpeech);

    java.util.List<VocabularyWord> findByWordIgnoreCaseAndStatusOrderByPartOfSpeechAscIdAsc(
            String word, VocabularyStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update VocabularyWord word
               set word.status = :published,
                   word.publishedAt = :publishedAt,
                   word.updatedAt = :publishedAt
             where word.status = :pending
            """)
    /** Phe duyet cac noi dung dang cho duyet trong cung mot thao tac. */
    int approveAllPending(
            @Param("pending") VocabularyStatus pending,
            @Param("published") VocabularyStatus published,
            @Param("publishedAt") Instant publishedAt);

    /** Tim kiem theo cac bo loc va giu thu tu hien thi hien tai. */
    default List<VocabularyWord> search(String keyword, VocabularyStatus status, String part) {
        return findAll(
                        specification(keyword, status, part, false),
                        org.springframework.data.domain.PageRequest.of(0, 500))
                .getContent();
    }

    /** Truy van du lieu theo bo loc va gioi han kich thuoc trang. */
    default org.springframework.data.domain.Page<VocabularyWord> searchPage(
            String keyword,
            VocabularyStatus status,
            String part,
            int page,
            int size,
            boolean exact) {
        return findAll(
                specification(keyword, status, part, exact),
                org.springframework.data.domain.PageRequest.of(
                        Math.max(0, page), Math.max(1, Math.min(size, 100))));
    }

    /** Tao dieu kien truy van theo cac bo loc ma van giu thu tu sap xep cu. */
    private Specification<VocabularyWord> specification(
            String keyword, VocabularyStatus status, String part, boolean exact) {
        String text =
                keyword == null ? "" : VocabularyText.normalize(keyword).toLowerCase(Locale.ROOT);
        String folded = VocabularyText.searchKey(text);
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (part != null && !part.isBlank())
                predicates.add(
                        cb.equal(
                                cb.lower(root.get("partOfSpeech")),
                                part.strip().toLowerCase(Locale.ROOT)));
            if (!text.isEmpty()) {
                if (exact) predicates.add(cb.equal(cb.lower(root.get("word")), text));
                else {
                    var senses = query.subquery(Integer.class);
                    var meaning = senses.from(VocabularyMeaning.class);
                    senses.select(cb.literal(1))
                            .where(
                                    cb.equal(meaning.get("vocabulary"), root),
                                    cb.or(
                                            cb.gt(
                                                    cb.locate(
                                                            cb.lower(meaning.get("translationEn")),
                                                            text),
                                                    0),
                                            cb.gt(
                                                    cb.locate(
                                                            cb.lower(meaning.get("definitionEn")),
                                                            text),
                                                    0)));
                    predicates.add(
                            cb.or(
                                    cb.gt(cb.locate(cb.lower(root.get("word")), text), 0),
                                    cb.gt(cb.locate(root.get("searchWord"), folded), 0),
                                    cb.exists(senses)));
                }
            }
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                var rank =
                        cb.<Integer>selectCase()
                                .when(cb.equal(cb.lower(root.get("word")), text), 0)
                                .when(cb.equal(root.get("searchWord"), folded), 1)
                                .otherwise(2);
                query.orderBy(
                        cb.asc(rank),
                        cb.asc(root.get("word")),
                        cb.asc(root.get("partOfSpeech")),
                        cb.asc(root.get("id")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
