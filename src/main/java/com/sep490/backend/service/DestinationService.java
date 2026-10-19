package com.sep490.backend.service;

import com.sep490.backend.dto.model.Destination;
import com.sep490.backend.dto.request.DestinationRequest;
import com.sep490.backend.dto.response.DestinationResponse;
import com.sep490.backend.entity.enums.DestinationStatus;
import com.sep490.backend.entity.enums.VietnamRegion;
import com.sep490.backend.exception.LearningException;
import com.sep490.backend.mapper.DestinationResponseMapper;
import com.sep490.backend.repository.DestinationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DestinationService {

    private final DestinationRepository repository;

    public List<DestinationResponse> search(
            String keyword,
            DestinationStatus status,
            VietnamRegion region
    ) {
        return repository.search(keyword, status, region)
                .stream()
                .map(DestinationResponseMapper::toResponse)
                .toList();
    }

    public List<DestinationResponse> searchPublished(
            String keyword,
            VietnamRegion region
    ) {
        return search(
                keyword,
                DestinationStatus.PUBLISHED,
                region
        );
    }

    public DestinationResponse findById(Long id) {
        return DestinationResponseMapper.toResponse(
                findDestination(id)
        );
    }

    public DestinationResponse findPublishedById(Long id) {
        Destination destination = repository.findById(id)
                .filter(item ->
                        item.status() == DestinationStatus.PUBLISHED
                )
                .orElseThrow(() ->
                        LearningException.notFound("Destination")
                );

        return DestinationResponseMapper.toResponse(destination);
    }

    @Transactional
    public DestinationResponse create(DestinationRequest request) {
        String slug = normalizeSlug(request.slug());

        if (repository.existsBySlug(slug)) {
            throw slugConflict();
        }

        DestinationStatus status = request.status() == null
                ? DestinationStatus.DRAFT
                : request.status();

        Instant now = Instant.now();

        Destination destination = new Destination(
                null,
                request.nameVi().trim(),
                request.nameEn().trim(),
                slug,
                request.region(),
                trimToNull(request.shortDescriptionVi()),
                trimToNull(request.shortDescriptionEn()),
                trimToNull(request.descriptionVi()),
                trimToNull(request.descriptionEn()),
                trimToNull(request.imageUrl()),
                status,
                status == DestinationStatus.PUBLISHED ? now : null,
                status == DestinationStatus.ARCHIVED ? now : null,
                null,
                null
        );

        return DestinationResponseMapper.toResponse(
                repository.save(destination)
        );
    }

    @Transactional
    public DestinationResponse update(
            Long id,
            DestinationRequest request
    ) {
        Destination current = findDestination(id);

        String slug = normalizeSlug(request.slug());

        if (repository.existsBySlugExceptId(slug, id)) {
            throw slugConflict();
        }

        DestinationStatus status = request.status() == null
                ? current.status()
                : request.status();

        Instant publishedAt = current.publishedAt();
        Instant archivedAt = current.archivedAt();

        if (status == DestinationStatus.PUBLISHED
                && current.status() != DestinationStatus.PUBLISHED
                && publishedAt == null) {
            publishedAt = Instant.now();
        }

        if (status == DestinationStatus.ARCHIVED
                && current.status() != DestinationStatus.ARCHIVED
                && archivedAt == null) {
            archivedAt = Instant.now();
        }

        Destination updated = new Destination(
                current.id(),
                request.nameVi().trim(),
                request.nameEn().trim(),
                slug,
                request.region(),
                trimToNull(request.shortDescriptionVi()),
                trimToNull(request.shortDescriptionEn()),
                trimToNull(request.descriptionVi()),
                trimToNull(request.descriptionEn()),
                trimToNull(request.imageUrl()),
                status,
                publishedAt,
                archivedAt,
                current.createdAt(),
                current.updatedAt()
        );

        return DestinationResponseMapper.toResponse(
                repository.save(updated)
        );
    }

    private Destination findDestination(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        LearningException.notFound("Destination")
                );
    }

    private static LearningException slugConflict() {
        return new LearningException(
                LearningException.Kind.CONFLICT,
                "Destination slug already exists."
        );
    }

    private static String normalizeSlug(String slug) {
        return slug.trim().toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}