package com.sep490.backend.modules.vocabulary.service;

import com.sep490.backend.modules.vocabulary.dto.model.VocabularyExample;
import com.sep490.backend.modules.vocabulary.dto.model.VocabularyMeaning;
import com.sep490.backend.modules.vocabulary.dto.model.VocabularyWord;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyExampleRequest;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyMeaningRequest;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyWordRequest;
import com.sep490.backend.common.dto.request.ContentReviewRequest;
import com.sep490.backend.modules.vocabulary.dto.response.VocabularyWordResponse;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.vocabulary.mapper.VocabularyWordResponseMapper;
import com.sep490.backend.modules.vocabulary.repository.VocabularyCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VocabularyWordService {

    private final VocabularyCatalogRepository repository;
    private final com.sep490.backend.modules.vocabulary.repository.jpa.VocabularyWordJpaRepository words;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public List<VocabularyWordResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(VocabularyWordResponseMapper::toResponse)
                .toList();
    }

    public List<VocabularyWordResponse> search(
            String keyword,
            VocabularyStatus status,
            String partOfSpeech) {
        return repository.search(
                        keyword,
                        status,
                        partOfSpeech)
                .stream()
                .map(VocabularyWordResponseMapper::toResponse)
                .toList();
    }

    public VocabularyWordResponse findById(Long id) {
        return VocabularyWordResponseMapper.toResponse(
                findWord(id)
        );
    }

    @Transactional
    public VocabularyWordResponse create(VocabularyWordRequest request) {
        checkIdentity(request,-1L);
        VocabularyStatus status = request.status() == null
                ? VocabularyStatus.DRAFT
                : request.status();

        Instant now = Instant.now();

        VocabularyWord word = new VocabularyWord(
                null,
                VocabularyText.normalize(request.word()),
                trimToNull(request.pronunciation()),
                request.partOfSpeech().trim(),
                trimToNull(request.audioUrl()),
                status,
                status == VocabularyStatus.PUBLISHED ? now : null,
                status == VocabularyStatus.ARCHIVED ? now : null,
                mapMeanings(request.meanings()),
                null,
                null
        );

        return VocabularyWordResponseMapper.toResponse(
                repository.save(word)
        );
    }

    @Transactional
    public VocabularyWordResponse createDraft(VocabularyWordRequest request) {
        if (request.status() != null && request.status() != VocabularyStatus.DRAFT) {
            throw new LearningException(LearningException.Kind.INVALID,
                    "Nội dung mới phải được lưu ở trạng thái DRAFT.");
        }
        return create(request);
    }

    @Transactional
    public VocabularyWordResponse update(
            Long id,
            VocabularyWordRequest request
    ) {
        VocabularyWord current = findWord(id);
        checkIdentity(request,id);

        VocabularyStatus status = request.status() == null
                ? current.status()
                : request.status();

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

        VocabularyWord updated = new VocabularyWord(
                current.id(),
                VocabularyText.normalize(request.word()),
                trimToNull(request.pronunciation()),
                request.partOfSpeech().trim(),
                trimToNull(request.audioUrl()),
                status,
                publishedAt,
                archivedAt,
                mapMeanings(request.meanings()),
                current.createdAt(),
                current.updatedAt()
        );

        repository.save(updated);
        var entity = words.findById(id).orElseThrow();
        if (current.source() != null) entity.setSourceMetadata(com.sep490.backend.modules.vocabulary.dto.model.DictionaryMetadata.encode(current.source().markEdited()));
        return VocabularyWordResponseMapper.toResponse(com.sep490.backend.modules.vocabulary.mapper.VocabularyWordMapper.toDomain(words.saveAndFlush(entity)));
    }

    @Transactional
    public VocabularyWordResponse updateDraft(Long id, VocabularyWordRequest request) {
        var current = findWord(id);
        if (current.status() != VocabularyStatus.DRAFT) {
            throw new LearningException(LearningException.Kind.CONFLICT,
                    "Chỉ nội dung DRAFT mới được chỉnh sửa.");
        }
        if (request.status() != null && request.status() != VocabularyStatus.DRAFT) {
            throw new LearningException(LearningException.Kind.INVALID,
                    "Hãy dùng thao tác gửi duyệt thay vì thay đổi trạng thái trong biểu mẫu.");
        }
        return update(id, request);
    }

    @Transactional
    public VocabularyWordResponse submitReview(Long id) {
        var current = findWord(id);
        if (current.status() != VocabularyStatus.DRAFT) {
            throw invalidTransition(current.status(), VocabularyStatus.PENDING_REVIEW);
        }
        return changeStatus(current, VocabularyStatus.PENDING_REVIEW);
    }

    @Transactional
    public VocabularyWordResponse archive(Long id) {
        var current = findWord(id);
        if (current.status() == VocabularyStatus.PENDING_REVIEW)
            throw new LearningException(LearningException.Kind.CONFLICT,"Nội dung đang chờ duyệt không thể lưu trữ.");
        if (current.status() == VocabularyStatus.ARCHIVED) return VocabularyWordResponseMapper.toResponse(current);
        return changeStatus(current, VocabularyStatus.ARCHIVED);
    }

    @Transactional
    public VocabularyWordResponse restore(Long id) {
        var current = findWord(id);
        if (current.status() != VocabularyStatus.ARCHIVED) throw invalidTransition(current.status(), VocabularyStatus.DRAFT);
        return changeStatus(current, VocabularyStatus.DRAFT);
    }

    @Transactional
    public VocabularyWordResponse revise(Long id) {
        var current = findWord(id);
        if (current.status() != VocabularyStatus.PUBLISHED)
            throw invalidTransition(current.status(), VocabularyStatus.DRAFT);
        return changeStatus(current, VocabularyStatus.DRAFT);
    }

    @Transactional
    public VocabularyWordResponse review(Long id, ContentReviewRequest.Decision decision) {
        var current = findWord(id);
        if (current.status() != VocabularyStatus.PENDING_REVIEW) {
            throw new LearningException(LearningException.Kind.CONFLICT,
                    "Chỉ nội dung PENDING_REVIEW mới có thể được duyệt.");
        }
        return changeStatus(current, decision == ContentReviewRequest.Decision.APPROVE
                ? VocabularyStatus.PUBLISHED : VocabularyStatus.DRAFT);
    }

    private VocabularyWordResponse changeStatus(VocabularyWord current, VocabularyStatus status) {
        var now = Instant.now();
        var changed = new VocabularyWord(current.id(), current.word(), current.pronunciation(),
                current.partOfSpeech(), current.audioUrl(), status,
                status == VocabularyStatus.PUBLISHED ? now : current.publishedAt(),
                status == VocabularyStatus.ARCHIVED ? now : current.archivedAt(),
                current.meanings(), current.createdAt(), current.updatedAt());
        return VocabularyWordResponseMapper.toResponse(repository.save(changed));
    }

    private static LearningException invalidTransition(VocabularyStatus from, VocabularyStatus to) {
        return new LearningException(LearningException.Kind.CONFLICT,
                "Không thể chuyển trạng thái từ " + from + " sang " + to + ".");
    }

    private VocabularyWord findWord(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        LearningException.notFound("Vocabulary")
                );
    }

    private List<VocabularyMeaning> mapMeanings(
            List<VocabularyMeaningRequest> requests
    ) {
        return requests.stream()
                .map(this::mapMeaning)
                .toList();
    }

    private VocabularyMeaning mapMeaning(
            VocabularyMeaningRequest request
    ) {
        List<VocabularyExample> examples =
                request.examples() == null
                        ? List.of()
                        : request.examples()
                          .stream()
                          .map(this::mapExample)
                          .toList();

        return new VocabularyMeaning(
                null,
                trimToNull(request.translationEn()),
                trimToNull(request.definitionEn()),
                trimToNull(request.usageNote()),
                request.displayOrder() == null
                        ? 0
                        : request.displayOrder(),
                examples,
                trimToNull(request.definitionVi()),
                request.labels() == null ? List.of() : request.labels()
        );
    }

    private VocabularyExample mapExample(
            VocabularyExampleRequest request
    ) {
        return new VocabularyExample(
                null,
                request.exampleVi().trim(),
                trimToNull(request.translationEn()),
                trimToNull(request.audioUrl()),
                request.displayOrder() == null
                        ? 0
                        : request.displayOrder()
        );
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public List<VocabularyWordResponse> searchPublished(
            String keyword,
            String partOfSpeech) {
        return repository.search(
                        keyword,
                        VocabularyStatus.PUBLISHED,
                        partOfSpeech)
                .stream()
                .map(VocabularyWordResponseMapper::toPublishedResponse)
                .toList();
    }

    public VocabularyWordResponse findPublishedById(Long id) {
        VocabularyWord word = repository.findById(id)
                .filter(item ->
                        item.status() == VocabularyStatus.PUBLISHED
                )
                .orElseThrow(() ->
                        LearningException.notFound("Vocabulary")
                );

        return VocabularyWordResponseMapper.toPublishedResponse(word);
    }

    private void checkIdentity(VocabularyWordRequest request,Long id) {
        jdbc.queryForObject("SELECT module FROM content_import_locks WHERE module='VOCABULARY' FOR UPDATE",String.class);
        if (words.existsByWordIgnoreCaseAndPartOfSpeechIgnoreCaseAndIdNot(VocabularyText.normalize(request.word()),request.partOfSpeech().strip(),id))
            throw new LearningException(LearningException.Kind.CONFLICT,"Từ và từ loại đã tồn tại. Hãy sửa mục từ hiện có để thêm nghĩa.");
    }
    public org.springframework.data.domain.Page<VocabularyWordResponse> searchPage(String keyword,VocabularyStatus status,String part,int page,int size,boolean publishedOnly,boolean exact) {
        if (keyword != null && keyword.length() > 200) throw new LearningException(LearningException.Kind.INVALID,"Từ khóa tối đa 200 ký tự.");
        return repository.searchPage(keyword,publishedOnly ? VocabularyStatus.PUBLISHED : status,part,page,size,exact)
                .map(w -> publishedOnly ? VocabularyWordResponseMapper.toPublishedResponse(w) : VocabularyWordResponseMapper.toResponse(w));
    }
    public java.util.Map<String,Object> links(Long id) {
        var word = findWord(id);
        if (word.status() != VocabularyStatus.PUBLISHED) throw LearningException.notFound("Vocabulary");
        var family = words.findByWordIgnoreCaseAndStatusOrderByPartOfSpeechAscIdAsc(word.word(),VocabularyStatus.PUBLISHED).stream()
                .filter(w -> !w.getId().equals(id)).map(w -> java.util.Map.of("id",w.getId(),"word",w.getWord(),"partOfSpeech",w.getPartOfSpeech())).toList();
        var related = new java.util.ArrayList<java.util.Map<String,Object>>();
        if (word.lexical() != null && word.lexical().relatedWords() != null) for (var relation : word.lexical().relatedWords()) {
            var row = new java.util.LinkedHashMap<String,Object>(); row.put("word",relation.word()); row.put("kind",relation.kind()); row.put("sense",relation.sense());
            var targets = words.findByWordIgnoreCaseAndStatusOrderByPartOfSpeechAscIdAsc(relation.word(),VocabularyStatus.PUBLISHED);
            row.put("id",targets.isEmpty() ? null : targets.getFirst().getId()); related.add(row);
        }
        return java.util.Map.of("otherPartsOfSpeech",family,"relatedWords",related);
    }
}
