package com.sep490.backend.modules.learning.lesson.web;
import com.sep490.backend.modules.learning.authoring.application.ActivityService;
import com.sep490.backend.modules.learning.lesson.application.LessonService;
import com.sep490.backend.modules.learning.lesson.web.dto.LessonResponse;
import com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore;



import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import com.sep490.backend.common.security.LearnerPrincipal;
@RestController @RequiredArgsConstructor
public class LessonController {
    private final LessonService lessons;
    private final com.sep490.backend.modules.learning.authoring.application.ActivityService activities;
    private final com.sep490.backend.modules.learning.shared.infrastructure.query.ContentStore store;
    @GetMapping("/api/courses/{id}/lessons")
    public List<LessonResponse> list(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id) { return lessons.lessons(user.id(), id); }
    @GetMapping("/api/lessons/{id}")
    public Object detail(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id) { return activities.lesson(id,user.id(),false); }
    @PostMapping("/api/lessons/{id}/start")
    public LessonResponse start(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id) { return lessons.start(user.id(), id); }
    @PostMapping("/api/lessons/{id}/complete")
    public LessonResponse complete(@AuthenticationPrincipal LearnerPrincipal user, @PathVariable Long id) { return lessons.complete(user.id(), id); }
}
