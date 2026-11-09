package com.sep490.backend.modules.explore.service;

import com.sep490.backend.common.dto.request.ContentReviewRequest;
import com.sep490.backend.common.exception.LearningException;
import com.sep490.backend.modules.explore.dto.request.DestinationManagementRequest;
import com.sep490.backend.modules.explore.dto.request.DestinationRequest;
import com.sep490.backend.modules.explore.dto.response.DestinationManagementResponse;
import com.sep490.backend.modules.explore.dto.response.DestinationResponse;
import com.sep490.backend.modules.explore.entity.Destination;
import com.sep490.backend.modules.explore.entity.enums.DestinationStatus;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.mapper.DestinationMapper;
import com.sep490.backend.modules.explore.repository.DestinationRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDestinationService {

    private final DestinationRepository repository;
    private final DestinationExploreService explore;

    // Luu dia diem va lien ket cung nhau de co the rollback khi du lieu khong hop le.
    /** Luu dia diem va cac lien ket trong cung transaction de rollback cung nhau. */
    @Transactional
    public DestinationManagementResponse updateManagement(
            long id, DestinationManagementRequest request) {
        var destination = updateDraft(id, request.destination());
        var content = explore.save(id, request.exploreContent());
        return new DestinationManagementResponse(destination, content);
    }

    /** Truy van du lieu theo bo loc va gioi han kich thuoc trang. */
    public Page<DestinationResponse> searchPage(
            String keyword, DestinationStatus status, VietnamRegion region, int page, int size) {
        return repository
                .searchPage(keyword, status, region, page, size)
                .map(DestinationMapper::toResponse);
    }

    /** Lay chi tiet noi dung theo ID. */
    public DestinationResponse findById(Long id) {
        return findDestination(id);
    }

    /** Tao noi dung moi voi cac gia tri mac dinh hien tai. */
    @Transactional
    public DestinationResponse create(DestinationRequest request) {
        String slug = normalizeSlug(request.slug());

        if (repository.existsBySlug(slug)) {
            throw slugConflict();
        }

        DestinationStatus status =
                request.status() == null ? DestinationStatus.DRAFT : request.status();

        Instant now = Instant.now();

        DestinationResponse destination =
                new DestinationResponse(
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
                        null);

        return DestinationMapper.toResponse(saveDestination(destination));
    }

    /** Tao ban nhap va chan viec tu gan trang thai xuat ban. */
    @Transactional
    public DestinationResponse createDraft(DestinationRequest request) {
        if (request.status() != null && request.status() != DestinationStatus.DRAFT) {
            throw new LearningException(
                    LearningException.Kind.INVALID,
                    "Nội dung mới phải được lưu ở trạng thái DRAFT.");
        }
        return create(request);
    }

    /** Cap nhat noi dung sau khi kiem tra du lieu va trang thai hien tai. */
    @Transactional
    public DestinationResponse update(Long id, DestinationRequest request) {
        DestinationResponse current = findDestination(id);

        String slug = normalizeSlug(request.slug());

        if (repository.existsBySlugExceptId(slug, id)) {
            throw slugConflict();
        }

        DestinationStatus status = request.status() == null ? current.status() : request.status();

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

        DestinationResponse updated =
                new DestinationResponse(
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
                        current.updatedAt());

        return DestinationMapper.toResponse(saveDestination(updated));
    }

    /** Chi cho phep chinh sua noi dung dang o trang thai ban nhap. */
    @Transactional
    public DestinationResponse updateDraft(Long id, DestinationRequest request) {
        var current = findDestination(id);
        if (current.status() != DestinationStatus.DRAFT) {
            throw new LearningException(
                    LearningException.Kind.CONFLICT, "Chỉ nội dung DRAFT mới được chỉnh sửa.");
        }
        if (request.status() != null && request.status() != DestinationStatus.DRAFT) {
            throw new LearningException(
                    LearningException.Kind.INVALID,
                    "Hãy dùng thao tác gửi duyệt thay vì thay đổi trạng thái trong biểu mẫu.");
        }
        return update(id, request);
    }

    /** Chuyen ban nhap sang cho duyet sau khi kiem tra trang thai. */
    @Transactional
    public DestinationResponse submitReview(Long id) {
        var current = findDestination(id);
        if (current.status() != DestinationStatus.DRAFT) {
            throw invalidTransition(current.status(), DestinationStatus.PENDING_REVIEW);
        }
        return changeStatus(current, DestinationStatus.PENDING_REVIEW);
    }

    /** Luu tru noi dung theo quy tac chuyen trang thai hien tai. */
    @Transactional
    public DestinationResponse archive(Long id) {
        var current = findDestination(id);
        if (current.status() == DestinationStatus.PENDING_REVIEW)
            throw new LearningException(
                    LearningException.Kind.CONFLICT, "Nội dung đang chờ duyệt không thể lưu trữ.");
        if (current.status() == DestinationStatus.ARCHIVED) return current;
        return changeStatus(current, DestinationStatus.ARCHIVED);
    }

    /** Khoi phuc noi dung da luu tru ve ban nhap. */
    @Transactional
    public DestinationResponse restore(Long id) {
        var current = findDestination(id);
        if (current.status() != DestinationStatus.ARCHIVED)
            throw invalidTransition(current.status(), DestinationStatus.DRAFT);
        return changeStatus(current, DestinationStatus.DRAFT);
    }

    /** Chuyen noi dung da xuat ban ve ban nhap de chinh sua. */
    @Transactional
    public DestinationResponse revise(Long id) {
        var current = findDestination(id);
        if (current.status() != DestinationStatus.PUBLISHED)
            throw invalidTransition(current.status(), DestinationStatus.DRAFT);
        return changeStatus(current, DestinationStatus.DRAFT);
    }

    /** Phe duyet hoac tra noi dung ve ban nhap theo quyet dinh kiem duyet. */
    @Transactional
    public DestinationResponse review(Long id, ContentReviewRequest.Decision decision) {
        var current = findDestination(id);
        if (current.status() != DestinationStatus.PENDING_REVIEW) {
            throw new LearningException(
                    LearningException.Kind.CONFLICT,
                    "Chỉ nội dung PENDING_REVIEW mới có thể được duyệt.");
        }
        return changeStatus(
                current,
                decision == ContentReviewRequest.Decision.APPROVE
                        ? DestinationStatus.PUBLISHED
                        : DestinationStatus.DRAFT);
    }

    /** Doi trang thai va thoi diem xuat ban hoac luu tru theo quy tac cu. */
    private DestinationResponse changeStatus(
            DestinationResponse current, DestinationStatus status) {
        var now = Instant.now();
        var changed =
                new DestinationResponse(
                        current.id(),
                        current.nameVi(),
                        current.nameEn(),
                        current.slug(),
                        current.region(),
                        current.shortDescriptionVi(),
                        current.shortDescriptionEn(),
                        current.descriptionVi(),
                        current.descriptionEn(),
                        current.imageUrl(),
                        status,
                        status == DestinationStatus.PUBLISHED ? now : current.publishedAt(),
                        status == DestinationStatus.ARCHIVED ? now : current.archivedAt(),
                        current.createdAt(),
                        current.updatedAt());
        return DestinationMapper.toResponse(saveDestination(changed));
    }

    /** Tao loi cho thao tac chuyen trang thai khong hop le. */
    private static LearningException invalidTransition(
            DestinationStatus from, DestinationStatus to) {
        return new LearningException(
                LearningException.Kind.CONFLICT,
                "Không thể chuyển trạng thái từ " + from + " sang " + to + ".");
    }

    /** Tim dia diem theo ID va tra loi neu khong ton tai. */
    private DestinationResponse findDestination(Long id) {
        return repository
                .findById(id)
                .map(DestinationMapper::toResponse)
                .orElseThrow(() -> LearningException.notFound("Destination"));
    }

    /** Tao loi khi slug dia diem da duoc su dung. */
    private static LearningException slugConflict() {
        return new LearningException(
                LearningException.Kind.CONFLICT, "Destination slug already exists.");
    }

    /** Chuan hoa slug truoc khi kiem tra trung va luu du lieu. */
    private static String normalizeSlug(String slug) {
        return slug.trim().toLowerCase(Locale.ROOT);
    }

    /** Chuan hoa gia tri tuy chon, giu quy uoc null cua du lieu cu. */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Gan du lieu dia diem vao entity va luu trong transaction cua thao tac quan tri. */
    private Destination saveDestination(DestinationResponse destination) {
        var entity =
                destination.id() == null
                        ? new Destination()
                        : repository.findById(destination.id()).orElseThrow();
        DestinationMapper.updateEntity(entity, destination);
        return repository.saveAndFlush(entity);
    }
}
