package com.sep490.backend.modules.vocabulary.service;

import com.sep490.backend.common.dto.request.ContentReviewRequest;
import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyRequest;
import com.sep490.backend.modules.vocabulary.dto.response.DictionaryMetadata;
import com.sep490.backend.modules.vocabulary.dto.response.VocabularyResponse;
import com.sep490.backend.modules.vocabulary.entity.VocabularyWord;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.modules.vocabulary.mapper.VocabularyMapper;
import com.sep490.backend.modules.vocabulary.repository.VocabularyRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminVocabularyService {

    private final VocabularyRepository repository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** Tim kiem theo cac bo loc va giu thu tu hien thi hien tai. */
    public List<VocabularyResponse> search(
            String keyword, VocabularyStatus status, String partOfSpeech) {
        return repository.search(keyword, status, partOfSpeech).stream()
                .map(VocabularyMapper::toResponse)
                .toList();
    }

    /** Lay chi tiet noi dung theo ID. */
    public VocabularyResponse findById(Long id) {
        return findWord(id);
    }

    /** Tao noi dung moi voi cac gia tri mac dinh hien tai. */
    @Transactional
    public VocabularyResponse create(VocabularyRequest request) {
        checkIdentity(request, -1L);

        VocabularyStatus status =
                request.status() == null ? VocabularyStatus.DRAFT : request.status();
        Instant now = Instant.now();

        VocabularyResponse word =
                new VocabularyResponse(
                        null,
                        VocabularyText.normalize(request.word()),
                        trimToNull(request.pronunciation()),
                        request.partOfSpeech().trim(),
                        trimToNull(request.audioUrl()),
                        status,
                        status == VocabularyStatus.PUBLISHED ? now : null,
                        status == VocabularyStatus.ARCHIVED ? now : null,
                        VocabularyMapper.mapMeanings(request.meanings()),
                        null,
                        null);

        return VocabularyMapper.toResponse(saveWord(word));
    }

    /** Tao ban nhap va chan viec tu gan trang thai xuat ban. */
    @Transactional
    public VocabularyResponse createDraft(VocabularyRequest request) {
        if (request.status() != null && request.status() != VocabularyStatus.DRAFT) {
            throw new LearningException(
                    LearningException.Kind.INVALID,
                    "Nội dung mới phải được lưu ở trạng thái DRAFT.");
        }
        return create(request);
    }

    /** Cap nhat noi dung sau khi kiem tra du lieu va trang thai hien tai. */
    @Transactional
    public VocabularyResponse update(Long id, VocabularyRequest request) {
        VocabularyResponse current = findWord(id);
        checkIdentity(request, id);

        VocabularyStatus status = request.status() == null ? current.status() : request.status();

        Instant publishedAt = current.publishedAt();
        Instant archivedAt = current.archivedAt();

        if (status == VocabularyStatus.PUBLISHED
                && current.status() != VocabularyStatus.PUBLISHED
                && publishedAt == null) {
            publishedAt = Instant.now();
        }

        if (status == VocabularyStatus.ARCHIVED
                && current.status() != VocabularyStatus.ARCHIVED
                && archivedAt == null) {
            archivedAt = Instant.now();
        }

        VocabularyResponse updated =
                new VocabularyResponse(
                        current.id(),
                        VocabularyText.normalize(request.word()),
                        trimToNull(request.pronunciation()),
                        request.partOfSpeech().trim(),
                        trimToNull(request.audioUrl()),
                        status,
                        publishedAt,
                        archivedAt,
                        VocabularyMapper.mapMeanings(request.meanings()),
                        current.createdAt(),
                        current.updatedAt());

        saveWord(updated);
        var entity = repository.findById(id).orElseThrow();

        if (current.source() != null) {
            entity.setSourceMetadata(DictionaryMetadata.encode(current.source().markEdited()));
        }

        return VocabularyMapper.toResponse(repository.saveAndFlush(entity));
    }

    /** Chi cho phep chinh sua noi dung dang o trang thai ban nhap. */
    @Transactional
    public VocabularyResponse updateDraft(Long id, VocabularyRequest request) {
        var current = findWord(id);

        if (current.status() != VocabularyStatus.DRAFT) {
            throw new LearningException(
                    LearningException.Kind.CONFLICT, "Chỉ nội dung DRAFT mới được chỉnh sửa.");
        }

        if (request.status() != null && request.status() != VocabularyStatus.DRAFT) {
            throw new LearningException(
                    LearningException.Kind.INVALID,
                    "Hãy dùng thao tác gửi duyệt thay vì thay đổi trạng thái trong biểu mẫu.");
        }

        return update(id, request);
    }

    /** Chuyen ban nhap sang cho duyet sau khi kiem tra trang thai. */
    @Transactional
    public VocabularyResponse submitReview(Long id) {
        var current = findWord(id);

        if (current.status() != VocabularyStatus.DRAFT) {
            throw invalidTransition(current.status(), VocabularyStatus.PENDING_REVIEW);
        }

        return changeStatus(current, VocabularyStatus.PENDING_REVIEW);
    }

    /** Luu tru noi dung theo quy tac chuyen trang thai hien tai. */
    @Transactional
    public VocabularyResponse archive(Long id) {
        var current = findWord(id);

        if (current.status() == VocabularyStatus.PENDING_REVIEW) {
            throw new LearningException(
                    LearningException.Kind.CONFLICT, "Nội dung đang chờ duyệt không thể lưu trữ.");
        }

        if (current.status() == VocabularyStatus.ARCHIVED) {
            return current;
        }

        return changeStatus(current, VocabularyStatus.ARCHIVED);
    }

    /** Khoi phuc noi dung da luu tru ve ban nhap. */
    @Transactional
    public VocabularyResponse restore(Long id) {
        var current = findWord(id);

        if (current.status() != VocabularyStatus.ARCHIVED) {
            throw invalidTransition(current.status(), VocabularyStatus.DRAFT);
        }

        return changeStatus(current, VocabularyStatus.DRAFT);
    }

    /** Chuyen noi dung da xuat ban ve ban nhap de chinh sua. */
    @Transactional
    public VocabularyResponse revise(Long id) {
        var current = findWord(id);

        if (current.status() != VocabularyStatus.PUBLISHED) {
            throw invalidTransition(current.status(), VocabularyStatus.DRAFT);
        }

        return changeStatus(current, VocabularyStatus.DRAFT);
    }

    /** Phe duyet hoac tra noi dung ve ban nhap theo quyet dinh kiem duyet. */
    @Transactional
    public VocabularyResponse review(Long id, ContentReviewRequest.Decision decision) {
        var current = findWord(id);

        if (current.status() != VocabularyStatus.PENDING_REVIEW) {
            throw new LearningException(
                    LearningException.Kind.CONFLICT,
                    "Chỉ nội dung PENDING_REVIEW mới có thể được duyệt.");
        }

        return changeStatus(
                current,
                decision == ContentReviewRequest.Decision.APPROVE
                        ? VocabularyStatus.PUBLISHED
                        : VocabularyStatus.DRAFT);
    }

    /** Phe duyet cac noi dung dang cho duyet trong cung mot thao tac. */
    @Transactional
    public int approveAllPending() {
        return repository.approveAllPending(
                VocabularyStatus.PENDING_REVIEW, VocabularyStatus.PUBLISHED, Instant.now());
    }

    /** Doi trang thai va thoi diem xuat ban hoac luu tru theo quy tac cu. */
    private VocabularyResponse changeStatus(VocabularyResponse current, VocabularyStatus status) {
        var now = Instant.now();

        var changed =
                new VocabularyResponse(
                        current.id(),
                        current.word(),
                        current.pronunciation(),
                        current.partOfSpeech(),
                        current.audioUrl(),
                        status,
                        status == VocabularyStatus.PUBLISHED ? now : current.publishedAt(),
                        status == VocabularyStatus.ARCHIVED ? now : current.archivedAt(),
                        current.meanings(),
                        current.createdAt(),
                        current.updatedAt());

        return VocabularyMapper.toResponse(saveWord(changed));
    }

    /** Tao loi cho thao tac chuyen trang thai khong hop le. */
    private static LearningException invalidTransition(VocabularyStatus from, VocabularyStatus to) {
        return new LearningException(
                LearningException.Kind.CONFLICT,
                "Không thể chuyển trạng thái từ " + from + " sang " + to + ".");
    }

    /** Tim tu vung va tra loi khong tim thay neu ID khong ton tai. */
    private VocabularyResponse findWord(Long id) {
        return repository
                .findById(id)
                .map(VocabularyMapper::toResponse)
                .orElseThrow(() -> LearningException.notFound("Vocabulary"));
    }

    /** Chuan hoa gia tri tuy chon, giu quy uoc null cua du lieu cu. */
    private static String trimToNull(String value) {
        if (value == null) return null;

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Khoa module va kiem tra trung tu, tu loai, bao toan dau tieng Viet. */
    private void checkIdentity(VocabularyRequest request, Long id) {
        jdbc.queryForObject(
                "SELECT module FROM content_import_locks WHERE module='VOCABULARY' FOR UPDATE",
                String.class);

        String normalizedWord = VocabularyText.normalize(request.word());
        // Kiem tra dau tieng Viet sau khi database loc theo collation.
        boolean duplicate =
                repository
                        .findByWordIgnoreCaseAndPartOfSpeechIgnoreCase(
                                normalizedWord, request.partOfSpeech().strip())
                        .stream()
                        .anyMatch(
                                word ->
                                        !word.getId().equals(id)
                                                && VocabularyText.normalize(word.getWord())
                                                        .equalsIgnoreCase(normalizedWord));
        if (duplicate) {
            throw new LearningException(
                    LearningException.Kind.CONFLICT,
                    "Từ và từ loại đã tồn tại. Hãy sửa mục từ hiện có để thêm nghĩa.");
        }
    }

    /** Luu entity va danh dau nguon tu dien da duoc chinh sua. */
    private VocabularyWord saveWord(VocabularyResponse word) {
        var entity =
                word.id() == null
                        ? new VocabularyWord()
                        : repository.findById(word.id()).orElseThrow();
        if (word.id() != null
                && entity.getSourceMetadata() != null
                && !entity.getSourceMetadata().isBlank()) {
            var source =
                    DictionaryMetadata.decode(
                            entity.getSourceMetadata(), DictionaryMetadata.Source.class);
            entity.setSourceMetadata(DictionaryMetadata.encode(source.markEdited()));
        }
        VocabularyMapper.updateEntity(entity, word);
        return repository.saveAndFlush(entity);
    }

    /** Truy van du lieu theo bo loc va gioi han kich thuoc trang. */
    public org.springframework.data.domain.Page<VocabularyResponse> searchPage(
            String keyword,
            VocabularyStatus status,
            String part,
            int page,
            int size,
            boolean exact) {

        if (keyword != null && keyword.length() > 200) {
            throw new LearningException(
                    LearningException.Kind.INVALID, "Từ khóa tối đa 200 ký tự.");
        }

        return repository
                .searchPage(keyword, status, part, page, size, exact)
                .map(VocabularyMapper::toResponse);
    }

    /** Lay tu chuan theo ID de module khac kiem tra lien ket. */
    public java.util.Optional<VocabularyWord> findExistingWord(Long id) {
        return repository.findById(id);
    }

    /** Lay cac tu chuan duoc chon khi quan ly lien ket noi dung. */
    public List<VocabularyWord> findExistingWords(List<Long> ids) {
        return repository.findAllById(ids);
    }
}
