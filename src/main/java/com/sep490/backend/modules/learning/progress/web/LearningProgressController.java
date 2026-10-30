package com.sep490.backend.modules.learning.progress.web;
import com.sep490.backend.modules.learning.progress.application.LearningProgressService;
import com.sep490.backend.modules.learning.progress.web.dto.LearningProgressResponse;


import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import com.sep490.backend.common.security.LearnerPrincipal;
@RestController @RequiredArgsConstructor
public class LearningProgressController {
    private final LearningProgressService progress;
    @GetMapping("/api/me/courses/{id}/progress")
    public LearningProgressResponse progress(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id) { return progress.progress(user.id(), id); }
}
