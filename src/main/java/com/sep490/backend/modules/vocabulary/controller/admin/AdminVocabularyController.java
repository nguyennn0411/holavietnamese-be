package com.sep490.backend.modules.vocabulary.controller.admin;

import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.common.dto.request.ContentReviewRequest;
import com.sep490.backend.common.security.ContentPermissions;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyRequest;
import com.sep490.backend.modules.vocabulary.dto.response.VocabularyResponse;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.modules.vocabulary.service.AdminVocabularyService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/vocabulary")
@RequiredArgsConstructor
public class AdminVocabularyController {

    private final AdminVocabularyService service;

    /** Tim kiem va phan trang du lieu theo bo loc tu request. */
    @PreAuthorize(ContentPermissions.VOCABULARY_READ_ACCESS)
    @GetMapping("/search")
    public ApiResponse<org.springframework.data.domain.Page<VocabularyResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) VocabularyStatus status,
            @RequestParam(required = false) String partOfSpeech,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                service.searchPage(keyword, status, partOfSpeech, page, size, false));
    }

    /** Tim kiem theo cac bo loc va giu thu tu hien thi hien tai. */
    @GetMapping
    @PreAuthorize(ContentPermissions.VOCABULARY_READ_ACCESS)
    public ApiResponse<List<VocabularyResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) VocabularyStatus status,
            @RequestParam(required = false) String partOfSpeech) {
        return ApiResponse.<List<VocabularyResponse>>builder()
                .result(service.search(keyword, status, partOfSpeech))
                .build();
    }

    /** Lay chi tiet noi dung theo ID. */
    @GetMapping("/{id}")
    @PreAuthorize(ContentPermissions.VOCABULARY_READ_ACCESS)
    public ApiResponse<VocabularyResponse> findById(@PathVariable Long id) {
        return ApiResponse.<VocabularyResponse>builder().result(service.findById(id)).build();
    }

    /** Tao noi dung moi voi cac gia tri mac dinh hien tai. */
    @PostMapping
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VocabularyResponse> create(@Valid @RequestBody VocabularyRequest request) {
        return ApiResponse.<VocabularyResponse>builder()
                .result(service.createDraft(request))
                .message("Vocabulary created successfully.")
                .build();
    }

    /** Cap nhat noi dung sau khi kiem tra du lieu va trang thai hien tai. */
    @PutMapping("/{id}")
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    public ApiResponse<VocabularyResponse> update(
            @PathVariable Long id, @Valid @RequestBody VocabularyRequest request) {
        return ApiResponse.<VocabularyResponse>builder()
                .result(service.updateDraft(id, request))
                .message("Vocabulary updated successfully.")
                .build();
    }

    /** Chuyen ban nhap sang cho duyet sau khi kiem tra trang thai. */
    @PostMapping("/{id}/submit-review")
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    public ApiResponse<VocabularyResponse> submitReview(@PathVariable Long id) {
        return ApiResponse.<VocabularyResponse>builder()
                .result(service.submitReview(id))
                .message("Vocabulary submitted for review.")
                .build();
    }

    /** Luu tru noi dung theo quy tac chuyen trang thai hien tai. */
    @PostMapping("/{id}/archive")
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    public ApiResponse<VocabularyResponse> archive(@PathVariable Long id) {
        return ApiResponse.success(service.archive(id));
    }

    /** Khoi phuc noi dung da luu tru ve ban nhap. */
    @PostMapping("/{id}/restore")
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    public ApiResponse<VocabularyResponse> restore(@PathVariable Long id) {
        return ApiResponse.success(service.restore(id));
    }

    /** Chuyen noi dung da xuat ban ve ban nhap de chinh sua. */
    @PostMapping("/{id}/revise")
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    public ApiResponse<VocabularyResponse> revise(@PathVariable Long id) {
        return ApiResponse.<VocabularyResponse>builder()
                .result(service.revise(id))
                .message("Vocabulary returned to draft for revision.")
                .build();
    }

    /** Phe duyet hoac tra noi dung ve ban nhap theo quyet dinh kiem duyet. */
    @PostMapping("/{id}/review")
    @PreAuthorize(ContentPermissions.VOCABULARY_REVIEW_ACCESS)
    public ApiResponse<VocabularyResponse> review(
            @PathVariable Long id, @Valid @RequestBody ContentReviewRequest request) {
        return ApiResponse.<VocabularyResponse>builder()
                .result(service.review(id, request.decision()))
                .message(
                        request.decision() == ContentReviewRequest.Decision.APPROVE
                                ? "Vocabulary approved and published."
                                : "Vocabulary returned to draft.")
                .build();
    }

    /** Phe duyet cac noi dung dang cho duyet trong cung mot thao tac. */
    @PostMapping("/review/approve-all")
    @PreAuthorize(ContentPermissions.VOCABULARY_REVIEW_ACCESS)
    public ApiResponse<Integer> approveAllPending() {
        int approved = service.approveAllPending();
        return ApiResponse.<Integer>builder()
                .result(approved)
                .message(
                        approved == 0
                                ? "No vocabulary entries are awaiting review."
                                : approved + " vocabulary entries approved and published.")
                .build();
    }
}
