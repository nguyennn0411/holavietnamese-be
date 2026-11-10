package com.sep490.backend.modules.explore.controller.admin;

import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.common.dto.request.ContentReviewRequest;
import com.sep490.backend.common.security.ContentPermissions;
import com.sep490.backend.modules.explore.dto.request.DestinationManagementRequest;
import com.sep490.backend.modules.explore.dto.request.DestinationRequest;
import com.sep490.backend.modules.explore.dto.response.DestinationManagementResponse;
import com.sep490.backend.modules.explore.dto.response.DestinationResponse;
import com.sep490.backend.modules.explore.entity.enums.DestinationStatus;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.service.AdminDestinationService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/destinations")
@RequiredArgsConstructor
public class AdminDestinationController {

    private final AdminDestinationService service;

    /** Tim kiem theo cac bo loc va giu thu tu hien thi hien tai. */
    @GetMapping
    @PreAuthorize(ContentPermissions.EXPLORE_READ_ACCESS)
    public ApiResponse<Page<DestinationResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DestinationStatus status,
            @RequestParam(required = false) VietnamRegion region,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.<Page<DestinationResponse>>builder()
                .result(service.searchPage(keyword, status, region, page, size))
                .build();
    }

    /** Lay chi tiet noi dung theo ID. */
    @GetMapping("/{id}")
    @PreAuthorize(ContentPermissions.EXPLORE_READ_ACCESS)
    public ApiResponse<DestinationResponse> findById(@PathVariable Long id) {
        return ApiResponse.<DestinationResponse>builder().result(service.findById(id)).build();
    }

    /** Tao noi dung moi voi cac gia tri mac dinh hien tai. */
    @PostMapping
    @PreAuthorize(ContentPermissions.EXPLORE_WRITE_ACCESS)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DestinationResponse> create(@Valid @RequestBody DestinationRequest request) {
        return ApiResponse.<DestinationResponse>builder()
                .result(service.createDraft(request))
                .message("Destination created successfully.")
                .build();
    }

    /** Cap nhat noi dung sau khi kiem tra du lieu va trang thai hien tai. */
    @PutMapping("/{id}")
    @PreAuthorize(ContentPermissions.EXPLORE_WRITE_ACCESS)
    public ApiResponse<DestinationResponse> update(
            @PathVariable Long id, @Valid @RequestBody DestinationRequest request) {
        return ApiResponse.<DestinationResponse>builder()
                .result(service.updateDraft(id, request))
                .message("Destination updated successfully.")
                .build();
    }

    /** Luu dia diem va cac lien ket trong cung transaction de rollback cung nhau. */
    @PutMapping("/{id}/management")
    @PreAuthorize(ContentPermissions.EXPLORE_MANAGEMENT_ACCESS)
    public ApiResponse<DestinationManagementResponse> updateManagement(
            @PathVariable long id, @Valid @RequestBody DestinationManagementRequest request) {
        return ApiResponse.<DestinationManagementResponse>builder()
                .result(service.updateManagement(id, request))
                .message("Destination and Explore links updated successfully.")
                .build();
    }

    /** Chuyen ban nhap sang cho duyet sau khi kiem tra trang thai. */
    @PostMapping("/{id}/submit-review")
    @PreAuthorize(ContentPermissions.EXPLORE_WRITE_ACCESS)
    public ApiResponse<DestinationResponse> submitReview(@PathVariable Long id) {
        return ApiResponse.<DestinationResponse>builder()
                .result(service.submitReview(id))
                .message("Destination submitted for review.")
                .build();
    }

    /** Luu tru noi dung theo quy tac chuyen trang thai hien tai. */
    @PostMapping("/{id}/archive")
    @PreAuthorize(ContentPermissions.EXPLORE_WRITE_ACCESS)
    public ApiResponse<DestinationResponse> archive(@PathVariable Long id) {
        return ApiResponse.success(service.archive(id));
    }

    /** Khoi phuc noi dung da luu tru ve ban nhap. */
    @PostMapping("/{id}/restore")
    @PreAuthorize(ContentPermissions.EXPLORE_WRITE_ACCESS)
    public ApiResponse<DestinationResponse> restore(@PathVariable Long id) {
        return ApiResponse.success(service.restore(id));
    }

    /** Chuyen noi dung da xuat ban ve ban nhap de chinh sua. */
    @PostMapping("/{id}/revise")
    @PreAuthorize(ContentPermissions.EXPLORE_WRITE_ACCESS)
    public ApiResponse<DestinationResponse> revise(@PathVariable Long id) {
        return ApiResponse.<DestinationResponse>builder()
                .result(service.revise(id))
                .message("Destination returned to draft for revision.")
                .build();
    }

    /** Phe duyet hoac tra noi dung ve ban nhap theo quyet dinh kiem duyet. */
    @PostMapping("/{id}/review")
    @PreAuthorize(ContentPermissions.EXPLORE_REVIEW_ACCESS)
    public ApiResponse<DestinationResponse> review(
            @PathVariable Long id, @Valid @RequestBody ContentReviewRequest request) {
        return ApiResponse.<DestinationResponse>builder()
                .result(service.review(id, request.decision()))
                .message(
                        request.decision() == ContentReviewRequest.Decision.APPROVE
                                ? "Destination approved and published."
                                : "Destination returned to draft.")
                .build();
    }
}
