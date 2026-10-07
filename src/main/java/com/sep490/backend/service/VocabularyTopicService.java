package com.sep490.backend.service;

import com.sep490.backend.dto.model.VocabularyTopic;
import com.sep490.backend.dto.request.VocabularyTopicRequest;
import com.sep490.backend.dto.response.VocabularyTopicResponse;
import com.sep490.backend.entity.enums.VocabularyStatus;
import com.sep490.backend.exception.LearningException;
import com.sep490.backend.mapper.VocabularyTopicResponseMapper;
import com.sep490.backend.repository.VocabularyTopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VocabularyTopicService {

    private final VocabularyTopicRepository repository;

    public List<VocabularyTopicResponse> findAll() {
        return repository.findAll()
                .stream()
                .map(VocabularyTopicResponseMapper::toResponse)
                .toList();
    }

    public VocabularyTopicResponse findById(Long id) {
        return VocabularyTopicResponseMapper.toResponse(
                findTopic(id)
        );
    }

    @Transactional
    public VocabularyTopicResponse create(VocabularyTopicRequest request) {
        String name = request.name().trim();
        String slug = normalizeSlug(request.slug());

        if (repository.existsBySlug(slug)) {
            throw new LearningException(
                    LearningException.Kind.CONFLICT,
                    "Vocabulary topic slug already exists."
            );
        }

        VocabularyTopic topic = new VocabularyTopic(
                null,
                name,
                slug,
                trimToNull(request.description()),
                request.displayOrder() == null ? 0 : request.displayOrder(),
                request.status() == null
                        ? VocabularyStatus.DRAFT
                        : request.status()
        );

        return VocabularyTopicResponseMapper.toResponse(
                repository.save(topic)
        );
    }

    @Transactional
    public VocabularyTopicResponse update(
            Long id,
            VocabularyTopicRequest request
    ) {
        VocabularyTopic current = findTopic(id);

        String name = request.name().trim();
        String slug = normalizeSlug(request.slug());

        if (repository.existsBySlugExceptId(slug, id)) {
            throw new LearningException(
                    LearningException.Kind.CONFLICT,
                    "Vocabulary topic slug already exists."
            );
        }

        VocabularyTopic updated = new VocabularyTopic(
                current.id(),
                name,
                slug,
                trimToNull(request.description()),
                request.displayOrder() == null
                        ? current.displayOrder()
                        : request.displayOrder(),
                request.status() == null
                        ? current.status()
                        : request.status()
        );

        return VocabularyTopicResponseMapper.toResponse(
                repository.save(updated)
        );
    }

    private VocabularyTopic findTopic(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        LearningException.notFound("Vocabulary topic")
                );
    }

    private static String normalizeSlug(String slug) {
        return slug.trim().toLowerCase();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}