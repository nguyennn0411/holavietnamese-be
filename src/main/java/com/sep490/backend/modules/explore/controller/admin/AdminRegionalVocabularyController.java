package com.sep490.backend.modules.explore.controller.admin;

import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.common.dto.request.ContentReviewRequest;
import com.sep490.backend.common.security.ContentPermissions;
import com.sep490.backend.modules.explore.dto.request.RegionalVocabularyRequest;
import com.sep490.backend.modules.explore.dto.response.RegionalVocabularyResponse;
import com.sep490.backend.modules.explore.entity.enums.VietnamRegion;
import com.sep490.backend.modules.explore.service.AdminRegionalVocabularyService;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/regional-language")
public class AdminRegionalVocabularyController {
    private final AdminRegionalVocabularyService service;

    /** Lay danh sach theo bo loc cua nguoi dung. */
    @PreAuthorize(ContentPermissions.VOCABULARY_READ_ACCESS)
    @GetMapping
    public ApiResponse<Page<RegionalVocabularyResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) VietnamRegion region,
            @RequestParam(required = false) Long vocabularyId,
            @RequestParam(required = false) VocabularyStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                service.searchPage(keyword, region, vocabularyId, status, page, size));
    }

    /** Lay chi tiet theo ID va quyen truy cap hien tai. */
    @PreAuthorize(ContentPermissions.VOCABULARY_READ_ACCESS)
    @GetMapping("/{id}")
    public ApiResponse<RegionalVocabularyResponse> get(@PathVariable long id) {
        return ApiResponse.success(service.get(id));
    }

    /** Tao noi dung moi voi cac gia tri mac dinh hien tai. */
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegionalVocabularyResponse> create(
            @Valid @RequestBody RegionalVocabularyRequest input) {
        return ApiResponse.success(service.createDraft(input));
    }

    /** Cap nhat noi dung sau khi kiem tra du lieu va trang thai hien tai. */
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    @PutMapping("/{id}")
    public ApiResponse<RegionalVocabularyResponse> update(
            @PathVariable long id, @Valid @RequestBody RegionalVocabularyRequest input) {
        return ApiResponse.success(service.updateDraft(id, input));
    }

    /** Chuyen ban nhap sang cho duyet sau khi kiem tra trang thai. */
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    @PostMapping("/{id}/submit-review")
    public ApiResponse<RegionalVocabularyResponse> submitReview(@PathVariable long id) {
        return ApiResponse.success(service.submitReview(id));
    }

    /** Luu tru noi dung theo quy tac chuyen trang thai hien tai. */
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    @PostMapping("/{id}/archive")
    public ApiResponse<RegionalVocabularyResponse> archive(@PathVariable long id) {
        return ApiResponse.success(service.archive(id));
    }

    /** Khoi phuc noi dung da luu tru ve ban nhap. */
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    @PostMapping("/{id}/restore")
    public ApiResponse<RegionalVocabularyResponse> restore(@PathVariable long id) {
        return ApiResponse.success(service.restore(id));
    }

    /** Chuyen noi dung da xuat ban ve ban nhap de chinh sua. */
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    @PostMapping("/{id}/revise")
    public ApiResponse<RegionalVocabularyResponse> revise(@PathVariable long id) {
        return ApiResponse.success(service.revise(id));
    }

    /** Phe duyet hoac tra noi dung ve ban nhap theo quyet dinh kiem duyet. */
    @PreAuthorize(ContentPermissions.VOCABULARY_REVIEW_ACCESS)
    @PostMapping("/{id}/review")
    public ApiResponse<RegionalVocabularyResponse> review(
            @PathVariable long id, @Valid @RequestBody ContentReviewRequest input) {
        return ApiResponse.success(service.review(id, input.decision()));
    }

    /** Phe duyet cac noi dung dang cho duyet trong cung mot thao tac. */
    @PreAuthorize(ContentPermissions.VOCABULARY_REVIEW_ACCESS)
    @PostMapping("/review/approve-all")
    public ApiResponse<Integer> approveAllPending() {
        int approved = service.approveAllPending();
        return ApiResponse.<Integer>builder()
                .result(approved)
                .message(
                        approved == 0
                                ? "No regional vocabulary variants are awaiting review."
                                : approved
                                        + " regional vocabulary variants approved and published.")
                .build();
    }
}
