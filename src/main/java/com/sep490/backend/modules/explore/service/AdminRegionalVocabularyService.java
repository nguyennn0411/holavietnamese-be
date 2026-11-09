package com.sep490.backend.modules.explore.service;

import com.sep490.backend.common.dto.request.ContentReviewRequest;
import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.explore.dto.request.RegionalVocabularyRequest;
import com.sep490.backend.modules.explore.dto.response.RegionalVocabularyResponse;
import com.sep490.backend.modules.explore.entity.RegionalVocabulary;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.mapper.RegionalVocabularyMapper;
import com.sep490.backend.modules.explore.repository.RegionalVocabularyRepository;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.modules.vocabulary.service.AdminVocabularyService;
import com.sep490.backend.modules.vocabulary.service.VocabularyText;

import jakarta.persistence.criteria.Predicate;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminRegionalVocabularyService {
    private final RegionalVocabularyRepository repository;
    private final AdminVocabularyService vocabulary;

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

    /** Truy van du lieu theo bo loc va gioi han kich thuoc trang. */
    public Page<RegionalVocabularyResponse> searchPage(
            String keyword,
            VietnamRegion region,
            Long vocabularyId,
            VocabularyStatus status,
            int page,
            int size) {
        var pageable =
                PageRequest.of(
                        Math.max(0, page),
                        Math.max(1, Math.min(size, 100)),
                        Sort.by("vocabulary.word", "vocabulary.id", "region", "word", "id"));
        return repository
                .findAll(specification(keyword, region, vocabularyId, status, false), pageable)
                .map(RegionalVocabularyMapper::toResponse);
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

    /** Lay chi tiet theo ID va quyen truy cap hien tai. */
    public RegionalVocabularyResponse get(long id) {
        return RegionalVocabularyMapper.toResponse(find(id));
    }

    /** Lay cac bien the thuoc vung va cac tu chuan da gan vao dia diem. */
    public List<RegionalVocabularyResponse> forDestination(
            Collection<Long> masterIds, VietnamRegion region, boolean publishedOnly) {
        if (masterIds.isEmpty()) return List.of();
        return search(null, region, null, null, publishedOnly).stream()
                .filter(v -> masterIds.contains(v.vocabularyId()))
                .toList();
    }

    /** Tao noi dung moi voi cac gia tri mac dinh hien tai. */
    @Transactional
    public RegionalVocabularyResponse create(RegionalVocabularyRequest request) {
        return save(new RegionalVocabulary(), request);
    }

    /** Cap nhat noi dung sau khi kiem tra du lieu va trang thai hien tai. */
    @Transactional
    public RegionalVocabularyResponse update(long id, RegionalVocabularyRequest request) {
        return save(find(id), request);
    }

    /** Tao ban nhap va chan viec tu gan trang thai xuat ban. */
    @Transactional
    public RegionalVocabularyResponse createDraft(RegionalVocabularyRequest request) {
        if (request.status() != null && request.status() != VocabularyStatus.DRAFT)
            throw new LearningException(
                    LearningException.Kind.INVALID,
                    "Nội dung mới phải được lưu ở trạng thái DRAFT.");
        return create(request);
    }

    /** Chi cho phep chinh sua noi dung dang o trang thai ban nhap. */
    @Transactional
    public RegionalVocabularyResponse updateDraft(long id, RegionalVocabularyRequest request) {
        var row = find(id);
        if (row.getStatus() != VocabularyStatus.DRAFT)
            throw new LearningException(
                    LearningException.Kind.CONFLICT, "Chỉ nội dung DRAFT mới được chỉnh sửa.");
        if (request.status() != null && request.status() != VocabularyStatus.DRAFT)
            throw new LearningException(
                    LearningException.Kind.INVALID,
                    "Hãy dùng thao tác gửi duyệt thay vì thay đổi trạng thái trong biểu mẫu.");
        return save(row, request);
    }

    /** Chuyen ban nhap sang cho duyet sau khi kiem tra trang thai. */
    @Transactional
    public RegionalVocabularyResponse submitReview(long id) {
        var row = find(id);
        if (row.getStatus() != VocabularyStatus.DRAFT)
            throw new LearningException(
                    LearningException.Kind.CONFLICT, "Chỉ nội dung DRAFT mới có thể gửi duyệt.");
        row.setStatus(VocabularyStatus.PENDING_REVIEW);
        return RegionalVocabularyMapper.toResponse(repository.saveAndFlush(row));
    }

    /** Luu tru noi dung theo quy tac chuyen trang thai hien tai. */
    @Transactional
    public RegionalVocabularyResponse archive(long id) {
        var row = find(id);
        if (row.getStatus() == VocabularyStatus.PENDING_REVIEW)
            throw new LearningException(
                    LearningException.Kind.CONFLICT, "Nội dung đang chờ duyệt không thể lưu trữ.");
        row.setStatus(VocabularyStatus.ARCHIVED);
        return RegionalVocabularyMapper.toResponse(repository.saveAndFlush(row));
    }

    /** Khoi phuc noi dung da luu tru ve ban nhap. */
    @Transactional
    public RegionalVocabularyResponse restore(long id) {
        var row = find(id);
        if (row.getStatus() != VocabularyStatus.ARCHIVED)
            throw new LearningException(
                    LearningException.Kind.CONFLICT, "Chỉ nội dung ARCHIVED mới có thể khôi phục.");
        row.setStatus(VocabularyStatus.DRAFT);
        return RegionalVocabularyMapper.toResponse(repository.saveAndFlush(row));
    }

    /** Chuyen noi dung da xuat ban ve ban nhap de chinh sua. */
    @Transactional
    public RegionalVocabularyResponse revise(long id) {
        var row = find(id);
        if (row.getStatus() != VocabularyStatus.PUBLISHED)
            throw new LearningException(
                    LearningException.Kind.CONFLICT,
                    "Chỉ nội dung PUBLISHED mới có thể chuyển về bản nháp để sửa.");
        row.setStatus(VocabularyStatus.DRAFT);
        return RegionalVocabularyMapper.toResponse(repository.saveAndFlush(row));
    }

    /** Phe duyet hoac tra noi dung ve ban nhap theo quyet dinh kiem duyet. */
    @Transactional
    public RegionalVocabularyResponse review(long id, ContentReviewRequest.Decision decision) {
        var row = find(id);
        if (row.getStatus() != VocabularyStatus.PENDING_REVIEW)
            throw new LearningException(
                    LearningException.Kind.CONFLICT,
                    "Chỉ nội dung PENDING_REVIEW mới có thể được duyệt.");
        row.setStatus(
                decision == ContentReviewRequest.Decision.APPROVE
                        ? VocabularyStatus.PUBLISHED
                        : VocabularyStatus.DRAFT);
        return RegionalVocabularyMapper.toResponse(repository.saveAndFlush(row));
    }

    /** Phe duyet cac noi dung dang cho duyet trong cung mot thao tac. */
    @Transactional
    public int approveAllPending() {
        return repository.approveAllPending(
                VocabularyStatus.PENDING_REVIEW, VocabularyStatus.PUBLISHED, Instant.now());
    }

    /** Kiem tra va luu cac lien ket theo dung thu tu trong request. */
    private RegionalVocabularyResponse save(
            RegionalVocabulary row, RegionalVocabularyRequest input) {
        var master =
                vocabulary
                        .findExistingWord(input.vocabularyId())
                        .orElseThrow(() -> LearningException.notFound("Master vocabulary"));
        var word = input.word().strip();
        long excludedId = row.getId() == null ? -1L : row.getId();
        String normalizedWord = VocabularyText.normalize(word);
        if (repository.findByVocabularyIdAndRegion(master.getId(), input.region()).stream()
                .anyMatch(
                        existing ->
                                existing.getId() != excludedId
                                        && VocabularyText.normalize(existing.getWord())
                                                .equalsIgnoreCase(normalizedWord)))
            throw new LearningException(
                    LearningException.Kind.CONFLICT,
                    "Biến thể này đã tồn tại cho từ chuẩn và vùng đã chọn.");
        row.setVocabulary(master);
        row.setRegion(input.region());
        row.setWord(word);
        row.setMeaning(input.meaning().strip());
        row.setMeaningEn(optional(input.meaningEn()));
        row.setPronunciation(optional(input.pronunciation()));
        row.setAudioUrl(optional(input.audioUrl()));
        row.setExampleSentence(optional(input.exampleSentence()));
        row.setExampleSentenceEn(optional(input.exampleSentenceEn()));
        row.setUsageNote(optional(input.usageNote()));
        row.setUsageNoteEn(optional(input.usageNoteEn()));
        if (input.status() != null) row.setStatus(input.status());
        return RegionalVocabularyMapper.toResponse(repository.saveAndFlush(row));
    }

    /** Tim bien the vung mien theo ID, bao loi neu khong ton tai. */
    private RegionalVocabulary find(long id) {
        return repository
                .findById(id)
                .orElseThrow(() -> LearningException.notFound("Regional vocabulary"));
    }

    /** Chuan hoa chuoi tuy chon truoc khi luu bien the. */
    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
