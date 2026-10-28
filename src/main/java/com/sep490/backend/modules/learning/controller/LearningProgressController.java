package com.sep490.backend.modules.learning.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.modules.learning.service.LearningProgressService;
import com.sep490.backend.modules.learning.dto.response.LearningProgressResponse;
@RestController @RequiredArgsConstructor
public class LearningProgressController {
    private final LearningProgressService progress;
    @GetMapping("/api/me/courses/{id}/progress")
    public LearningProgressResponse progress(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id) { return progress.progress(user.id(), id); }
}
