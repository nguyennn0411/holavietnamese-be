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
        return search(keyword, status, part, true);
    }

    /** Tim kiem voi pham vi noi dung phu hop tung man hinh. */
    default List<VocabularyWord> search(
            String keyword, VocabularyStatus status, String part, boolean includeDefinitions) {
        return findAll(
                        specification(keyword, status, part, false, includeDefinitions),
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
        return searchPage(keyword, status, part, page, size, exact, true);
    }

    /** Truy van co phan trang va tach pham vi public khoi tim kiem quan tri. */
    default org.springframework.data.domain.Page<VocabularyWord> searchPage(
            String keyword,
            VocabularyStatus status,
            String part,
            int page,
            int size,
            boolean exact,
            boolean includeDefinitions) {
        return findAll(
                specification(keyword, status, part, exact, includeDefinitions),
                org.springframework.data.domain.PageRequest.of(
                        Math.max(0, page), Math.max(1, Math.min(size, 100))));
    }

    /** Tao dieu kien truy van theo cac bo loc ma van giu thu tu sap xep cu. */
    private Specification<VocabularyWord> specification(
            String keyword,
            VocabularyStatus status,
            String part,
            boolean exact,
            boolean includeDefinitions) {
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
                var senses = query.subquery(Integer.class);
                var meaning = senses.from(VocabularyMeaning.class);
                var translationContains =
                        cb.gt(cb.locate(cb.lower(meaning.get("translationEn")), text), 0);
                var meaningMatch = translationContains;
                if (includeDefinitions) {
                    meaningMatch =
                            cb.or(
                                    translationContains,
                                    cb.gt(
                                            cb.locate(
                                                    cb.lower(meaning.get("definitionEn")), text),
                                            0));
                }
                senses.select(cb.literal(1))
                        .where(cb.equal(meaning.get("vocabulary"), root), meaningMatch);

                if (exact) {
                    var exactTranslation = query.subquery(Integer.class);
                    var exactMeaning = exactTranslation.from(VocabularyMeaning.class);
                    exactTranslation
                            .select(cb.literal(1))
                            .where(
                                    cb.equal(exactMeaning.get("vocabulary"), root),
                                    cb.equal(
                                            cb.trim(cb.lower(exactMeaning.get("translationEn"))),
                                            text));
                    predicates.add(
                            cb.or(
                                    cb.equal(cb.lower(root.get("word")), text),
                                    cb.equal(root.get("searchWord"), folded),
                                    cb.exists(exactTranslation)));
                } else {
                    predicates.add(
                            cb.or(
                                    cb.gt(cb.locate(cb.lower(root.get("word")), text), 0),
                                    cb.gt(cb.locate(root.get("searchWord"), folded), 0),
                                    cb.exists(senses)));
                }
            }
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                if (text.isEmpty()) {
                    query.orderBy(
                            cb.asc(root.get("word")),
                            cb.asc(root.get("partOfSpeech")),
                            cb.asc(root.get("id")));
                } else {
                    var rank = cb.<Integer>selectCase();
                    var exactTranslation = query.subquery(Integer.class);
                    var exactMeaning = exactTranslation.from(VocabularyMeaning.class);
                    exactTranslation
                            .select(cb.literal(1))
                            .where(
                                    cb.equal(exactMeaning.get("vocabulary"), root),
                                    cb.equal(
                                            cb.trim(cb.lower(exactMeaning.get("translationEn"))),
                                            text));

                    var prefixTranslation = query.subquery(Integer.class);
                    var prefixMeaning = prefixTranslation.from(VocabularyMeaning.class);
                    prefixTranslation
                            .select(cb.literal(1))
                            .where(
                                    cb.equal(prefixMeaning.get("vocabulary"), root),
                                    cb.equal(
                                            cb.locate(
                                                    cb.lower(prefixMeaning.get("translationEn")),
                                                    text),
                                            1));

                    var containsTranslation = query.subquery(Integer.class);
                    var containsMeaning = containsTranslation.from(VocabularyMeaning.class);
                    containsTranslation
                            .select(cb.literal(1))
                            .where(
                                    cb.equal(containsMeaning.get("vocabulary"), root),
                                    cb.gt(
                                            cb.locate(
                                                    cb.lower(containsMeaning.get("translationEn")),
                                                    text),
                                            0));

                    rank.when(cb.equal(cb.lower(root.get("word")), text), 0)
                            .when(cb.equal(root.get("searchWord"), folded), 1)
                            .when(cb.exists(exactTranslation), 2)
                            .when(
                                    cb.or(
                                            cb.equal(
                                                    cb.locate(
                                                            cb.lower(root.get("word")), text),
                                                    1),
                                            cb.equal(
                                                    cb.locate(root.get("searchWord"), folded), 1)),
                                    3)
                            .when(cb.exists(prefixTranslation), 4)
                            .when(
                                    cb.or(
                                            cb.gt(
                                                    cb.locate(
                                                            cb.lower(root.get("word")), text),
                                                    0),
                                            cb.gt(
                                                    cb.locate(root.get("searchWord"), folded),
                                                    0)),
                                    5)
                            .when(cb.exists(containsTranslation), 6);
                    query.orderBy(
                            cb.asc(rank.otherwise(7)),
                            cb.asc(root.get("word")),
                            cb.asc(root.get("partOfSpeech")),
                            cb.asc(root.get("id")));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
