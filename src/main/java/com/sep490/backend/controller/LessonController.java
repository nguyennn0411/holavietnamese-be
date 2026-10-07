package com.sep490.backend.controller;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.service.*;
import com.sep490.backend.dto.response.LessonResponse;
@RestController @RequiredArgsConstructor
public class LessonController {
    private final LessonService lessons;
    private final com.sep490.backend.learning.lesson.ActivityService activities;
    private final com.sep490.backend.learning.shared.ContentStore store;
    @GetMapping("/api/courses/{id}/lessons")
    public List<LessonResponse> list(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id) { return lessons.lessons(user.id(), id); }
    @GetMapping("/api/lessons/{id}")
    public Object detail(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id) { return activities.lesson(id,user.id(),false); }
    @PostMapping("/api/lessons/{id}/start")
    public LessonResponse start(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id) { return lessons.start(user.id(), id); }
    @PostMapping("/api/lessons/{id}/complete")
    public LessonResponse complete(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id) { return lessons.complete(user.id(), id); }
}
