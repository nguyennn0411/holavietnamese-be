package com.sep490.backend.modules.explore.service;

import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.explore.dto.response.RegionalVocabularyResponse;
import com.sep490.backend.modules.explore.entity.RegionalVocabulary;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.mapper.RegionalVocabularyMapper;
import com.sep490.backend.modules.explore.repository.RegionalVocabularyRepository;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;

import jakarta.persistence.criteria.Predicate;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionalVocabularyService {
    public static final int PUBLIC_PAGE_SIZE = 10;

    private final RegionalVocabularyRepository repository;

    /** Tim kiem theo cac bo loc va giu thu tu hien thi hien tai. */
    private List<RegionalVocabularyResponse> search(
            String keyword,
            VietnamRegion region,
            Long vocabularyId,
            Collection<Long> vocabularyIds,
            VocabularyStatus status,
            boolean publishedOnly) {
        return repository
                .findAll(
                        specification(
                                keyword,
                                region,
                                vocabularyId,
                                vocabularyIds,
                                status,
                                publishedOnly),
                        Sort.by("vocabulary.word", "vocabulary.id", "region", "word", "id"))
                .stream()
                .map(RegionalVocabularyMapper::toResponse)
                .toList();
    }

    /** Tao dieu kien truy van theo cac bo loc ma van giu thu tu sap xep cu. */
    private Specification<RegionalVocabulary> specification(
            String keyword,
            VietnamRegion region,
            Long vocabularyId,
            Collection<Long> vocabularyIds,
            VocabularyStatus status,
            boolean publishedOnly) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            var master = root.join("vocabulary");
            if (publishedOnly) {
                predicates.add(cb.equal(root.get("status"), VocabularyStatus.PUBLISHED));
                predicates.add(cb.equal(master.get("status"), VocabularyStatus.PUBLISHED));
            } else if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (region != null) predicates.add(cb.equal(root.get("region"), region));
            if (vocabularyId != null) predicates.add(cb.equal(master.get("id"), vocabularyId));
            if (vocabularyIds != null && !vocabularyIds.isEmpty()) {
                predicates.add(master.get("id").in(vocabularyIds));
            }
            if (keyword != null && !keyword.isBlank()) {
                var text = keyword.strip().toLowerCase(Locale.ROOT);
                predicates.add(
                        cb.or(
                                cb.gt(cb.locate(cb.lower(root.get("word")), text), 0),
                                cb.gt(cb.locate(cb.lower(root.get("meaning")), text), 0),
                                cb.gt(cb.locate(cb.lower(master.get("word")), text), 0)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Tim cac tu chuan phu hop va giu cac bien the cong khai de so sanh. */
    public List<RegionalVocabularyResponse.Comparison> comparisons(
            String keyword, VietnamRegion region) {
        Set<Long> matchingIds = new HashSet<>();
        for (RegionalVocabularyResponse variant :
                search(keyword, region, null, null, null, true)) {
            matchingIds.add(variant.vocabularyId());
        }
        if (matchingIds.isEmpty()) return List.of();

        Map<Long, List<RegionalVocabularyResponse>> groups = new LinkedHashMap<>();
        for (RegionalVocabularyResponse variant :
                search(null, null, null, matchingIds, null, true)) {
            if (!groups.containsKey(variant.vocabularyId())) {
                groups.put(variant.vocabularyId(), new ArrayList<>());
            }
            groups.get(variant.vocabularyId()).add(variant);
        }

        List<RegionalVocabularyResponse.Comparison> result = new ArrayList<>();
        for (var group : groups.entrySet()) {
            List<RegionalVocabularyResponse> variants = group.getValue();
            result.add(
                    new RegionalVocabularyResponse.Comparison(
                            group.getKey(), variants.getFirst().masterWord(), variants));
        }
        return result;
    }

    /** Phan trang theo tu chuan, sau do nap cac bien the cong khai theo batch. */
    public Page<RegionalVocabularyResponse.Comparison> comparisonPage(
            String keyword, VietnamRegion region, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new LearningException(
                    LearningException.Kind.INVALID, "Page phải không âm và size từ 1 đến 100.");
        }
        String text = keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
        if (text.length() > 200) {
            throw new LearningException(
                    LearningException.Kind.INVALID, "Từ khóa tối đa 200 ký tự.");
        }
        var ids =
                repository.findPublishedMasterIds(
                        text,
                        region,
                        VocabularyStatus.PUBLISHED,
                        PageRequest.of(page, size));
        if (ids.isEmpty()) return ids.map(id -> null);

        var variants = search(null, null, null, ids.getContent(), null, true);
        var groups = variants.stream().collect(
                java.util.stream.Collectors.groupingBy(
                        RegionalVocabularyResponse::vocabularyId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        return ids.map(
                id -> {
                    var rows = groups.getOrDefault(id, List.of());
                    return new RegionalVocabularyResponse.Comparison(
                            id, rows.getFirst().masterWord(), rows);
                });
    }

    /** Lay mot trang bien the cua diem den; kich thuoc trang duoc backend co dinh. */
    public Page<RegionalVocabularyResponse> forDestinationPage(
            Collection<Long> masterIds, VietnamRegion region, int page) {
        if (page < 0) {
            throw new LearningException(LearningException.Kind.INVALID, "Page phải không âm.");
        }
        if (masterIds.isEmpty()) {
            return Page.empty(PageRequest.of(page, PUBLIC_PAGE_SIZE));
        }
        return repository
                .findAll(
                        specification(null, region, null, masterIds, null, true),
                        PageRequest.of(
                                page,
                                PUBLIC_PAGE_SIZE,
                                Sort.by("vocabulary.word", "vocabulary.id", "region", "word", "id")))
                .map(RegionalVocabularyMapper::toResponse);
    }

    /** Lay cac bien the cong khai cua mot tu chuan. */
    public RegionalVocabularyResponse.Comparison comparison(long id) {
        var variants = search(null, null, id, null, null, true);
        if (variants.isEmpty()) throw LearningException.notFound("Regional vocabulary");
        return new RegionalVocabularyResponse.Comparison(
                id, variants.getFirst().masterWord(), variants);
    }

    /** Lay cac bien the thuoc vung va cac tu chuan da gan vao dia diem. */
    public List<RegionalVocabularyResponse> forDestination(
            Collection<Long> masterIds, VietnamRegion region) {
        if (masterIds.isEmpty()) return List.of();
        return search(null, region, null, masterIds, null, true);
    }
}
