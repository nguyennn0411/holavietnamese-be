package com.sep490.backend.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.service.LearningProgressService;
import com.sep490.backend.dto.response.LearningProgressResponse;
@RestController @RequiredArgsConstructor
public class LearningProgressController {
    private final LearningProgressService progress;
    @GetMapping("/api/me/courses/{id}/progress")
    public LearningProgressResponse progress(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id) { return progress.progress(user.id(), id); }
}
