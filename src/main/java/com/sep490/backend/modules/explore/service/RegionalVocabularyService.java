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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionalVocabularyService {
    private final RegionalVocabularyRepository repository;

    /** Tim kiem theo cac bo loc va giu thu tu hien thi hien tai. */
    private List<RegionalVocabularyResponse> search(
            String keyword,
            VietnamRegion region,
            Long vocabularyId,
            VocabularyStatus status,
            boolean publishedOnly) {
        return repository
                .findAll(
                        specification(keyword, region, vocabularyId, status, publishedOnly),
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
        for (RegionalVocabularyResponse variant : search(keyword, region, null, null, true)) {
            matchingIds.add(variant.vocabularyId());
        }

        Map<Long, List<RegionalVocabularyResponse>> groups = new LinkedHashMap<>();
        for (RegionalVocabularyResponse variant : search(null, null, null, null, true)) {
            if (!matchingIds.contains(variant.vocabularyId())) {
                continue;
            }
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

    /** Lay cac bien the cong khai cua mot tu chuan. */
    public RegionalVocabularyResponse.Comparison comparison(long id) {
        var variants = search(null, null, id, null, true);
        if (variants.isEmpty()) throw LearningException.notFound("Regional vocabulary");
        return new RegionalVocabularyResponse.Comparison(
                id, variants.getFirst().masterWord(), variants);
    }

    /** Lay cac bien the thuoc vung va cac tu chuan da gan vao dia diem. */
    public List<RegionalVocabularyResponse> forDestination(
            Collection<Long> masterIds, VietnamRegion region) {
        if (masterIds.isEmpty()) return List.of();
        return search(null, region, null, null, true).stream()
                .filter(v -> masterIds.contains(v.vocabularyId()))
                .toList();
    }
}
