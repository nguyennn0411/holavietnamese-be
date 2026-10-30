package com.sep490.backend.modules.explore.regional.service;

import com.sep490.backend.modules.explore.regional.dto.request.RegionalVocabularyRequest;
import com.sep490.backend.common.dto.request.ContentReviewRequest;
import com.sep490.backend.modules.explore.regional.dto.response.RegionalVocabularyResponse;
import com.sep490.backend.modules.explore.regional.entity.RegionalVocabularyJpaEntity;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.explore.regional.mapper.RegionalVocabularyMapper;
import com.sep490.backend.modules.explore.regional.repository.jpa.RegionalVocabularyJpaRepository;
import com.sep490.backend.modules.vocabulary.repository.jpa.VocabularyWordJpaRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class RegionalVocabularyService {
    private final RegionalVocabularyJpaRepository repository;
    private final VocabularyWordJpaRepository vocabulary;

    public List<RegionalVocabularyResponse> search(String keyword, VietnamRegion region, Long vocabularyId,
            VocabularyStatus status, boolean publishedOnly) {
        return repository.findAll((root, query, cb) -> {
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
                predicates.add(cb.or(cb.gt(cb.locate(cb.lower(root.get("word")), text), 0),
                        cb.gt(cb.locate(cb.lower(root.get("meaning")), text), 0),
                        cb.gt(cb.locate(cb.lower(master.get("word")), text), 0)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        }, Sort.by("vocabulary.word", "vocabulary.id", "region", "word", "id")).stream()
                .map(RegionalVocabularyMapper::toResponse).toList();
    }
    public RegionalVocabularyResponse get(long id) { return RegionalVocabularyMapper.toResponse(find(id)); }
    public List<RegionalVocabularyResponse.Comparison> comparisons(String keyword, VietnamRegion region) {
        // Region/search selects matching master words; retain their other public variants for comparison.
        var ids = search(keyword, region, null, null, true).stream().map(RegionalVocabularyResponse::vocabularyId).collect(Collectors.toSet());
        var grouped = search(null, null, null, null, true).stream().filter(v -> ids.contains(v.vocabularyId()))
                .collect(Collectors.groupingBy(RegionalVocabularyResponse::vocabularyId, LinkedHashMap::new, Collectors.toList()));
        return grouped.entrySet().stream().map(e -> new RegionalVocabularyResponse.Comparison(e.getKey(), e.getValue().getFirst().masterWord(), e.getValue())).toList();
    }
    public RegionalVocabularyResponse.Comparison comparison(long id) {
        var variants = search(null, null, id, null, true);
        if (variants.isEmpty()) throw LearningException.notFound("Regional vocabulary");
        return new RegionalVocabularyResponse.Comparison(id, variants.getFirst().masterWord(), variants);
    }
    public List<RegionalVocabularyResponse> forDestination(Collection<Long> masterIds, VietnamRegion region, boolean publishedOnly) {
        if (masterIds.isEmpty()) return List.of();
        return search(null, region, null, null, publishedOnly).stream().filter(v -> masterIds.contains(v.vocabularyId())).toList();
    }
    @Transactional public RegionalVocabularyResponse create(RegionalVocabularyRequest request) { return save(new RegionalVocabularyJpaEntity(), request); }
    @Transactional public RegionalVocabularyResponse update(long id, RegionalVocabularyRequest request) { return save(find(id), request); }
    @Transactional public RegionalVocabularyResponse createDraft(RegionalVocabularyRequest request) {
        if (request.status() != null && request.status() != VocabularyStatus.DRAFT)
            throw new LearningException(LearningException.Kind.INVALID,"Nội dung mới phải được lưu ở trạng thái DRAFT.");
        return create(request);
    }
    @Transactional public RegionalVocabularyResponse updateDraft(long id, RegionalVocabularyRequest request) {
        var row=find(id);
        if (row.getStatus()!=VocabularyStatus.DRAFT)
            throw new LearningException(LearningException.Kind.CONFLICT,"Chỉ nội dung DRAFT mới được chỉnh sửa.");
        if (request.status()!=null && request.status()!=VocabularyStatus.DRAFT)
            throw new LearningException(LearningException.Kind.INVALID,"Hãy dùng thao tác gửi duyệt thay vì thay đổi trạng thái trong biểu mẫu.");
        return save(row,request);
    }
    @Transactional public RegionalVocabularyResponse submitReview(long id) {
        var row=find(id);
        if(row.getStatus()!=VocabularyStatus.DRAFT)
            throw new LearningException(LearningException.Kind.CONFLICT,"Chỉ nội dung DRAFT mới có thể gửi duyệt.");
        row.setStatus(VocabularyStatus.PENDING_REVIEW);
        return RegionalVocabularyMapper.toResponse(repository.saveAndFlush(row));
    }
    @Transactional public RegionalVocabularyResponse archive(long id) {
        var row=find(id);
        if(row.getStatus()==VocabularyStatus.PENDING_REVIEW)
            throw new LearningException(LearningException.Kind.CONFLICT,"Nội dung đang chờ duyệt không thể lưu trữ.");
        row.setStatus(VocabularyStatus.ARCHIVED);
        return RegionalVocabularyMapper.toResponse(repository.saveAndFlush(row));
    }
    @Transactional public RegionalVocabularyResponse restore(long id) {
        var row=find(id);
        if(row.getStatus()!=VocabularyStatus.ARCHIVED)
            throw new LearningException(LearningException.Kind.CONFLICT,"Chỉ nội dung ARCHIVED mới có thể khôi phục.");
        row.setStatus(VocabularyStatus.DRAFT);
        return RegionalVocabularyMapper.toResponse(repository.saveAndFlush(row));
    }
    @Transactional public RegionalVocabularyResponse revise(long id) {
        var row=find(id);
        if(row.getStatus()!=VocabularyStatus.PUBLISHED)
            throw new LearningException(LearningException.Kind.CONFLICT,"Chỉ nội dung PUBLISHED mới có thể chuyển về bản nháp để sửa.");
        row.setStatus(VocabularyStatus.DRAFT);
        return RegionalVocabularyMapper.toResponse(repository.saveAndFlush(row));
    }
    @Transactional public RegionalVocabularyResponse review(long id, ContentReviewRequest.Decision decision) {
        var row=find(id);
        if(row.getStatus()!=VocabularyStatus.PENDING_REVIEW)
            throw new LearningException(LearningException.Kind.CONFLICT,"Chỉ nội dung PENDING_REVIEW mới có thể được duyệt.");
        row.setStatus(decision==ContentReviewRequest.Decision.APPROVE?VocabularyStatus.PUBLISHED:VocabularyStatus.DRAFT);
        return RegionalVocabularyMapper.toResponse(repository.saveAndFlush(row));
    }
    private RegionalVocabularyResponse save(RegionalVocabularyJpaEntity row, RegionalVocabularyRequest input) {
        var master = vocabulary.findById(input.vocabularyId()).orElseThrow(() -> LearningException.notFound("Master vocabulary"));
        var word = input.word().strip();
        if (repository.existsByVocabularyIdAndRegionAndWordIgnoreCaseAndIdNot(master.getId(), input.region(), word, row.getId() == null ? -1L : row.getId()))
            throw new LearningException(LearningException.Kind.CONFLICT, "Biến thể này đã tồn tại cho từ chuẩn và vùng đã chọn.");
        row.setVocabulary(master); row.setRegion(input.region()); row.setWord(word); row.setMeaning(input.meaning().strip());
        row.setPronunciation(optional(input.pronunciation())); row.setAudioUrl(optional(input.audioUrl()));
        row.setExampleSentence(optional(input.exampleSentence())); row.setUsageNote(optional(input.usageNote()));
        if (input.status() != null) row.setStatus(input.status());
        return RegionalVocabularyMapper.toResponse(repository.saveAndFlush(row));
    }
    private RegionalVocabularyJpaEntity find(long id) { return repository.findById(id).orElseThrow(() -> LearningException.notFound("Regional vocabulary")); }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
