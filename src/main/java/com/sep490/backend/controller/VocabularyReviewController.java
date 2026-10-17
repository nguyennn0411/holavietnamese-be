package com.sep490.backend.controller;

import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.dto.request.VocabularyReviewRequest;
import com.sep490.backend.dto.response.*;
import com.sep490.backend.service.VocabularyReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/me/vocabulary") @RequiredArgsConstructor
public class VocabularyReviewController {
    private final VocabularyReviewService reviews;

    @GetMapping("/review")
    public VocabularyReviewSessionResponse session(@AuthenticationPrincipal LearnerPrincipal user,
            @RequestParam(required = false) UUID sessionId) { return reviews.session(user.id(), sessionId); }

    @PostMapping("/{id}/review")
    public VocabularyReviewResultResponse rate(@AuthenticationPrincipal LearnerPrincipal user,
            @PathVariable Long id, @Valid @RequestBody VocabularyReviewRequest request) {
        return reviews.rate(user.id(), id, request);
    }
}
