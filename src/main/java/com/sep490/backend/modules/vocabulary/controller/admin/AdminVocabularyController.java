package com.sep490.backend.modules.vocabulary.controller.admin;

import com.sep490.backend.modules.vocabulary.dto.request.VocabularyWordRequest;
import com.sep490.backend.common.dto.request.ContentReviewRequest;
import com.sep490.backend.common.security.ContentPermissions;
import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.modules.vocabulary.dto.response.VocabularyWordResponse;
import com.sep490.backend.modules.vocabulary.entity.enums.VocabularyStatus;
import com.sep490.backend.modules.vocabulary.service.VocabularyWordService;
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

    private final VocabularyWordService service;

    @PreAuthorize(ContentPermissions.VOCABULARY_READ_ACCESS)
    @GetMapping("/search") public ApiResponse<org.springframework.data.domain.Page<VocabularyWordResponse>> page(
            @RequestParam(required=false) String keyword,@RequestParam(required=false) VocabularyStatus status,
            @RequestParam(required=false) String partOfSpeech,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.success(service.searchPage(keyword,status,partOfSpeech,page,size,false,false));
    }

    @GetMapping
    @PreAuthorize(ContentPermissions.VOCABULARY_READ_ACCESS)
    public ApiResponse<List<VocabularyWordResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) VocabularyStatus status,
            @RequestParam(required = false) String partOfSpeech
    ) {
        return ApiResponse.<List<VocabularyWordResponse>>builder()
                .result(
                        service.search(
                                keyword,
                                status,
                                partOfSpeech
                        )
                )
                .build();
    }

    @GetMapping("/{id}")
    @PreAuthorize(ContentPermissions.VOCABULARY_READ_ACCESS)
    public ApiResponse<VocabularyWordResponse> findById(
            @PathVariable Long id
    ) {
        return ApiResponse.<VocabularyWordResponse>builder()
                .result(service.findById(id))
                .build();
    }

    @PostMapping
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VocabularyWordResponse> create(
            @Valid @RequestBody VocabularyWordRequest request
    ) {
        return ApiResponse.<VocabularyWordResponse>builder()
                .result(service.createDraft(request))
                .message("Vocabulary created successfully.")
                .build();
    }

    @PutMapping("/{id}")
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    public ApiResponse<VocabularyWordResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody VocabularyWordRequest request
    ) {
        return ApiResponse.<VocabularyWordResponse>builder()
                .result(service.updateDraft(id, request))
                .message("Vocabulary updated successfully.")
                .build();
    }

    @PostMapping("/{id}/submit-review")
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    public ApiResponse<VocabularyWordResponse> submitReview(@PathVariable Long id) {
        return ApiResponse.<VocabularyWordResponse>builder().result(service.submitReview(id))
                .message("Vocabulary submitted for review.").build();
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    public ApiResponse<VocabularyWordResponse> archive(@PathVariable Long id) {
        return ApiResponse.success(service.archive(id));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    public ApiResponse<VocabularyWordResponse> restore(@PathVariable Long id) {
        return ApiResponse.success(service.restore(id));
    }

    @PostMapping("/{id}/revise")
    @PreAuthorize(ContentPermissions.VOCABULARY_WRITE_ACCESS)
    public ApiResponse<VocabularyWordResponse> revise(@PathVariable Long id) {
        return ApiResponse.<VocabularyWordResponse>builder().result(service.revise(id))
                .message("Vocabulary returned to draft for revision.").build();
    }

    @PostMapping("/{id}/review")
    @PreAuthorize(ContentPermissions.VOCABULARY_REVIEW_ACCESS)
    public ApiResponse<VocabularyWordResponse> review(@PathVariable Long id,
            @Valid @RequestBody ContentReviewRequest request) {
        return ApiResponse.<VocabularyWordResponse>builder().result(service.review(id, request.decision()))
                .message(request.decision() == ContentReviewRequest.Decision.APPROVE
                        ? "Vocabulary approved and published." : "Vocabulary returned to draft.").build();
    }
}
