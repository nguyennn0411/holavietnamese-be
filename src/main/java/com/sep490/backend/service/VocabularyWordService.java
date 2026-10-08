package com.sep490.backend.service;

import com.sep490.backend.dto.model.VocabularyExample;
import com.sep490.backend.dto.model.VocabularyMeaning;
import com.sep490.backend.dto.model.VocabularyTopic;
import com.sep490.backend.dto.model.VocabularyWord;
import com.sep490.backend.dto.request.VocabularyExampleRequest;
import com.sep490.backend.dto.request.VocabularyMeaningRequest;
import com.sep490.backend.dto.request.VocabularyWordRequest;
import com.sep490.backend.dto.response.VocabularyWordResponse;
import com.sep490.backend.entity.enums.VocabularyStatus;
import com.sep490.backend.exception.LearningException;
import com.sep490.backend.mapper.VocabularyWordResponseMapper;
import com.sep490.backend.repository.VocabularyCatalogRepository;
import com.sep490.backend.repository.VocabularyTopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VocabularyWordService {

    private final VocabularyCatalogRepository repository;
    private final VocabularyTopicRepository topicRepository;

    public List<VocabularyWordResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(VocabularyWordResponseMapper::toResponse)
                .toList();
    }

    public List<VocabularyWordResponse> search(
            String keyword,
            VocabularyStatus status,
            String cefrLevel,
            String partOfSpeech,
            Long topicId
    ) {
        return repository.search(
                        keyword,
                        status,
                        cefrLevel,
                        partOfSpeech,
                        topicId
                )
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
        VocabularyStatus status = request.status() == null
                ? VocabularyStatus.DRAFT
                : request.status();

        Instant now = Instant.now();

        VocabularyWord word = new VocabularyWord(
                null,
                request.word().trim(),
                trimToNull(request.pronunciation()),
                request.partOfSpeech().trim(),
                request.cefrLevel().trim(),
                trimToNull(request.audioUrl()),
                status,
                status == VocabularyStatus.PUBLISHED ? now : null,
                status == VocabularyStatus.ARCHIVED ? now : null,
                resolveTopics(request.topicIds()),
                mapMeanings(request.meanings()),
                null,
                null
        );

        return VocabularyWordResponseMapper.toResponse(
                repository.save(word)
        );
    }

    @Transactional
    public VocabularyWordResponse update(
            Long id,
            VocabularyWordRequest request
    ) {
        VocabularyWord current = findWord(id);

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
                request.word().trim(),
                trimToNull(request.pronunciation()),
                request.partOfSpeech().trim(),
                request.cefrLevel().trim(),
                trimToNull(request.audioUrl()),
                status,
                publishedAt,
                archivedAt,
                resolveTopics(request.topicIds()),
                mapMeanings(request.meanings()),
                current.createdAt(),
                current.updatedAt()
        );

        return VocabularyWordResponseMapper.toResponse(
                repository.save(updated)
        );
    }

    private VocabularyWord findWord(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        LearningException.notFound("Vocabulary")
                );
    }

    private Set<VocabularyTopic> resolveTopics(Set<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return Set.of();
        }

        Set<VocabularyTopic> topics = new LinkedHashSet<>();

        for (Long topicId : topicIds) {
            VocabularyTopic topic = topicRepository.findById(topicId)
                    .orElseThrow(() ->
                            LearningException.notFound(
                                    "Vocabulary topic"
                            )
                    );

            topics.add(topic);
        }

        return topics;
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
                request.translationEn().trim(),
                trimToNull(request.definitionEn()),
                trimToNull(request.usageNote()),
                request.displayOrder() == null
                        ? 0
                        : request.displayOrder(),
                examples
        );
    }

    private VocabularyExample mapExample(
            VocabularyExampleRequest request
    ) {
        return new VocabularyExample(
                null,
                request.exampleVi().trim(),
                request.translationEn().trim(),
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
            String cefrLevel,
            String partOfSpeech,
            Long topicId
    ) {
        return repository.search(
                        keyword,
                        VocabularyStatus.PUBLISHED,
                        cefrLevel,
                        partOfSpeech,
                        topicId
                )
                .stream()
                .map(VocabularyWordResponseMapper::toResponse)
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

        return VocabularyWordResponseMapper.toResponse(word);
    }
}