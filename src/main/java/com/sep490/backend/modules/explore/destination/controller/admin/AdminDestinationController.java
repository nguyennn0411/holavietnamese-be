package com.sep490.backend.modules.explore.destination.controller.admin;

import com.sep490.backend.modules.explore.destination.dto.request.DestinationRequest;
import com.sep490.backend.common.dto.request.ContentReviewRequest;
import com.sep490.backend.common.security.ContentPermissions;
import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.modules.explore.destination.dto.response.DestinationResponse;
import com.sep490.backend.modules.explore.destination.entity.enums.DestinationStatus;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.destination.service.DestinationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/destinations")
@RequiredArgsConstructor
public class AdminDestinationController {

    private final DestinationService service;

    @GetMapping
    @PreAuthorize(ContentPermissions.EXPLORE_READ_ACCESS)
    public ApiResponse<List<DestinationResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DestinationStatus status,
            @RequestParam(required = false) VietnamRegion region
    ) {
        return ApiResponse.<List<DestinationResponse>>builder()
                .result(service.search(keyword, status, region))
                .build();
    }

    @GetMapping("/{id}")
    @PreAuthorize(ContentPermissions.EXPLORE_READ_ACCESS)
    public ApiResponse<DestinationResponse> findById(
            @PathVariable Long id
    ) {
        return ApiResponse.<DestinationResponse>builder()
                .result(service.findById(id))
                .build();
    }

    @PostMapping
    @PreAuthorize(ContentPermissions.EXPLORE_WRITE_ACCESS)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DestinationResponse> create(
            @Valid @RequestBody DestinationRequest request
    ) {
        return ApiResponse.<DestinationResponse>builder()
                .result(service.createDraft(request))
                .message("Destination created successfully.")
                .build();
    }

    @PutMapping("/{id}")
    @PreAuthorize(ContentPermissions.EXPLORE_WRITE_ACCESS)
    public ApiResponse<DestinationResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody DestinationRequest request
    ) {
        return ApiResponse.<DestinationResponse>builder()
                .result(service.updateDraft(id, request))
                .message("Destination updated successfully.")
                .build();
    }

    @PostMapping("/{id}/submit-review")
    @PreAuthorize(ContentPermissions.EXPLORE_WRITE_ACCESS)
    public ApiResponse<DestinationResponse> submitReview(@PathVariable Long id) {
        return ApiResponse.<DestinationResponse>builder().result(service.submitReview(id))
                .message("Destination submitted for review.").build();
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize(ContentPermissions.EXPLORE_WRITE_ACCESS)
    public ApiResponse<DestinationResponse> archive(@PathVariable Long id) {
        return ApiResponse.success(service.archive(id));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize(ContentPermissions.EXPLORE_WRITE_ACCESS)
    public ApiResponse<DestinationResponse> restore(@PathVariable Long id) {
        return ApiResponse.success(service.restore(id));
    }

    @PostMapping("/{id}/revise")
    @PreAuthorize(ContentPermissions.EXPLORE_WRITE_ACCESS)
    public ApiResponse<DestinationResponse> revise(@PathVariable Long id) {
        return ApiResponse.<DestinationResponse>builder().result(service.revise(id))
                .message("Destination returned to draft for revision.").build();
    }

    @PostMapping("/{id}/review")
    @PreAuthorize(ContentPermissions.EXPLORE_REVIEW_ACCESS)
    public ApiResponse<DestinationResponse> review(@PathVariable Long id,
            @Valid @RequestBody ContentReviewRequest request) {
        return ApiResponse.<DestinationResponse>builder().result(service.review(id, request.decision()))
                .message(request.decision() == ContentReviewRequest.Decision.APPROVE
                        ? "Destination approved and published." : "Destination returned to draft.").build();
    }
}
