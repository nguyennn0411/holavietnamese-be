package com.sep490.backend.modules.learning.controller;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import com.sep490.backend.common.security.LearnerPrincipal;
import com.sep490.backend.modules.learning.service.ActivityService;
import com.sep490.backend.modules.learning.service.LessonService;
import com.sep490.backend.modules.learning.store.ContentStore;
import com.sep490.backend.modules.learning.dto.response.LessonResponse;
@RestController @RequiredArgsConstructor
public class LessonController {
    private final LessonService lessons;
    private final com.sep490.backend.modules.learning.service.ActivityService activities;
    private final com.sep490.backend.modules.learning.store.ContentStore store;
    @GetMapping("/api/courses/{id}/lessons")
    public List<LessonResponse> list(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id) { return lessons.lessons(user.id(), id); }
    @GetMapping("/api/lessons/{id}")
    public Object detail(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id) { return activities.lesson(id,user.id(),false); }
    @PostMapping("/api/lessons/{id}/start")
    public LessonResponse start(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id) { return lessons.start(user.id(), id); }
    @PostMapping("/api/lessons/{id}/complete")
    public LessonResponse complete(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id) { return lessons.complete(user.id(), id); }
}
