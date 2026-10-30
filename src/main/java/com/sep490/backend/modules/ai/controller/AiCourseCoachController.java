package com.sep490.backend.modules.ai.controller;

import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.modules.ai.dto.request.AiMessageSendRequest;
import com.sep490.backend.modules.ai.dto.response.AiMessageResponse;
import com.sep490.backend.modules.ai.dto.response.AiSessionResponse;
import com.sep490.backend.modules.ai.service.AiCourseCoachService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/coach")
@RequiredArgsConstructor
public class AiCourseCoachController {

    private final AiCourseCoachService coachService;

    /**
     * Get or create an AI Coach session for a specific lesson in a course.
     * GET /api/ai/coach/sessions?courseId=1&lessonId=2&lessonTitle=...
     */
    @GetMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<AiSessionResponse> getOrCreateSession(
            @RequestParam Long courseId,
            @RequestParam Long lessonId,
            @RequestParam(required = false) String lessonTitle
    ) {
        AiSessionResponse response = coachService.getOrCreateCoachSession(courseId, lessonId, lessonTitle);
        return ApiResponse.<AiSessionResponse>builder()
                .result(response)
                .message("Lấy phiên AI Course Coach thành công")
                .build();
    }

    /**
     * Send a message to the AI Course Coach.
     * POST /api/ai/coach/sessions/{id}/messages
     */
    @PostMapping("/sessions/{id}/messages")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<AiMessageResponse> sendMessage(
            @PathVariable String id,
            @Valid @RequestBody AiMessageSendRequest request
    ) {
        AiMessageResponse response = coachService.sendCoachMessage(id, request);
        return ApiResponse.<AiMessageResponse>builder()
                .result(response)
                .message("Gửi tin nhắn AI Coach thành công")
                .build();
    }
}
