package com.sep490.backend.modules.vocabulary.controller;

import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.modules.vocabulary.dto.request.VocabularyReviewRequest;
import com.sep490.backend.modules.vocabulary.dto.response.VocabularyReviewResultResponse;
import com.sep490.backend.modules.vocabulary.dto.response.VocabularyReviewSessionResponse;
import com.sep490.backend.modules.vocabulary.service.VocabularyReviewService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/me/vocabulary")
@RequiredArgsConstructor
public class VocabularyReviewController {
    private final VocabularyReviewService reviews;

    /** Tao hoac tiep tuc phien on tap voi cac tu den han. */
    @GetMapping("/review")
    public VocabularyReviewSessionResponse session(
            @AuthenticationPrincipal LearnerPrincipal user,
            @RequestParam(required = false) UUID sessionId) {
        return reviews.session(user.id(), sessionId);
    }

    /** Luu danh gia on tap, xu ly request gui lai va tinh lich on tiep theo. */
    @PostMapping("/{id}/review")
    public VocabularyReviewResultResponse rate(
            @AuthenticationPrincipal LearnerPrincipal user,
            @PathVariable Long id,
            @Valid @RequestBody VocabularyReviewRequest request) {
        return reviews.rate(user.id(), id, request);
    }
}
