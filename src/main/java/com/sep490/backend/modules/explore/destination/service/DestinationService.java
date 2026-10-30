package com.sep490.backend.modules.explore.destination.service;

import com.sep490.backend.modules.explore.destination.dto.model.Destination;
import com.sep490.backend.modules.explore.destination.dto.request.DestinationRequest;
import com.sep490.backend.common.dto.request.ContentReviewRequest;
import com.sep490.backend.modules.explore.destination.dto.response.DestinationResponse;
import com.sep490.backend.modules.explore.destination.entity.enums.DestinationStatus;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.explore.destination.mapper.DestinationResponseMapper;
import com.sep490.backend.modules.explore.destination.repository.DestinationRepository;
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
    public DestinationResponse createDraft(DestinationRequest request) {
        if (request.status() != null && request.status() != DestinationStatus.DRAFT) {
            throw new LearningException(LearningException.Kind.INVALID,
                    "Nội dung mới phải được lưu ở trạng thái DRAFT.");
        }
        return create(request);
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

    @Transactional
    public DestinationResponse updateDraft(Long id, DestinationRequest request) {
        var current = findDestination(id);
        if (current.status() != DestinationStatus.DRAFT) {
            throw new LearningException(LearningException.Kind.CONFLICT,
                    "Chỉ nội dung DRAFT mới được chỉnh sửa.");
        }
        if (request.status() != null && request.status() != DestinationStatus.DRAFT) {
            throw new LearningException(LearningException.Kind.INVALID,
                    "Hãy dùng thao tác gửi duyệt thay vì thay đổi trạng thái trong biểu mẫu.");
        }
        return update(id, request);
    }

    @Transactional
    public DestinationResponse submitReview(Long id) {
        var current = findDestination(id);
        if (current.status() != DestinationStatus.DRAFT) {
            throw invalidTransition(current.status(), DestinationStatus.PENDING_REVIEW);
        }
        return changeStatus(current, DestinationStatus.PENDING_REVIEW);
    }

    @Transactional
    public DestinationResponse archive(Long id) {
        var current = findDestination(id);
        if (current.status() == DestinationStatus.PENDING_REVIEW)
            throw new LearningException(LearningException.Kind.CONFLICT,"Nội dung đang chờ duyệt không thể lưu trữ.");
        if (current.status() == DestinationStatus.ARCHIVED) return DestinationResponseMapper.toResponse(current);
        return changeStatus(current, DestinationStatus.ARCHIVED);
    }

    @Transactional
    public DestinationResponse restore(Long id) {
        var current = findDestination(id);
        if (current.status() != DestinationStatus.ARCHIVED) throw invalidTransition(current.status(), DestinationStatus.DRAFT);
        return changeStatus(current, DestinationStatus.DRAFT);
    }

    @Transactional
    public DestinationResponse revise(Long id) {
        var current = findDestination(id);
        if (current.status() != DestinationStatus.PUBLISHED)
            throw invalidTransition(current.status(), DestinationStatus.DRAFT);
        return changeStatus(current, DestinationStatus.DRAFT);
    }

    @Transactional
    public DestinationResponse review(Long id, ContentReviewRequest.Decision decision) {
        var current = findDestination(id);
        if (current.status() != DestinationStatus.PENDING_REVIEW) {
            throw new LearningException(LearningException.Kind.CONFLICT,
                    "Chỉ nội dung PENDING_REVIEW mới có thể được duyệt.");
        }
        return changeStatus(current, decision == ContentReviewRequest.Decision.APPROVE
                ? DestinationStatus.PUBLISHED : DestinationStatus.DRAFT);
    }

    private DestinationResponse changeStatus(Destination current, DestinationStatus status) {
        var now = Instant.now();
        var changed = new Destination(current.id(), current.nameVi(), current.nameEn(), current.slug(),
                current.region(), current.shortDescriptionVi(), current.shortDescriptionEn(),
                current.descriptionVi(), current.descriptionEn(), current.imageUrl(), status,
                status == DestinationStatus.PUBLISHED ? now : current.publishedAt(),
                status == DestinationStatus.ARCHIVED ? now : current.archivedAt(),
                current.createdAt(), current.updatedAt());
        return DestinationResponseMapper.toResponse(repository.save(changed));
    }

    private static LearningException invalidTransition(DestinationStatus from, DestinationStatus to) {
        return new LearningException(LearningException.Kind.CONFLICT,
                "Không thể chuyển trạng thái từ " + from + " sang " + to + ".");
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
