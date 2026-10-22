package com.sep490.backend.controller;

import com.sep490.backend.dto.request.AiMessageSendRequest;
import com.sep490.backend.dto.request.AiReviewCreateRequest;
import com.sep490.backend.dto.request.AiSessionCreateRequest;
import com.sep490.backend.dto.response.*;
import com.sep490.backend.service.AiRoleplayService;
import com.sep490.backend.service.AiTutorService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AiTutorController {

    AiTutorService aiTutorService;
    AiRoleplayService aiRoleplayService;

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getStatus() {
        Map<String, Object> status = aiTutorService.getAiStatus();
        return ApiResponse.<Map<String, Object>>builder()
                .result(status)
                .build();
    }

    @PostMapping("/configure-key")
    public ApiResponse<Map<String, Object>> configureApiKey(@RequestBody Map<String, String> request) {
        String apiKey = request.get("apiKey");
        String model = request.get("model");
        Map<String, Object> res = aiTutorService.configureApiKey(apiKey, model);
        return ApiResponse.<Map<String, Object>>builder()
                .result(res)
                .message((String) res.get("message"))
                .build();
    }

    // --- AI TUTOR (UC-099, UC-100, UC-101, UC-102, UC-103) ---

    @PostMapping("/sessions")
    public ApiResponse<AiSessionResponse> createSession(@Valid @RequestBody AiSessionCreateRequest request) {
        AiSessionResponse response = aiTutorService.createSession(request);
        return ApiResponse.<AiSessionResponse>builder()
                .result(response)
                .message("Tạo phiên hội thoại AI Tutor thành công")
                .build();
    }

    @GetMapping("/sessions")
    public ApiResponse<List<AiSessionResponse>> getSessions(@RequestParam(required = false) String mode) {
        List<AiSessionResponse> response = aiTutorService.getLearnerSessions(mode);
        return ApiResponse.<List<AiSessionResponse>>builder()
                .result(response)
                .build();
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<AiSessionResponse> getSessionDetails(@PathVariable String id) {
        AiSessionResponse response = aiTutorService.getSessionDetails(id);
        return ApiResponse.<AiSessionResponse>builder()
                .result(response)
                .build();
    }

    @PostMapping("/sessions/{id}/messages")
    public ApiResponse<AiMessageResponse> sendMessage(
            @PathVariable String id,
            @Valid @RequestBody AiMessageSendRequest request
    ) {
        AiMessageResponse response = aiTutorService.sendMessage(id, request);
        return ApiResponse.<AiMessageResponse>builder()
                .result(response)
                .message("Gửi tin nhắn AI Tutor thành công")
                .build();
    }

    @PostMapping("/sessions/{id}/end")
    public ApiResponse<AiSessionResponse> endSession(@PathVariable String id) {
        AiSessionResponse response = aiTutorService.endSession(id);
        return ApiResponse.<AiSessionResponse>builder()
                .result(response)
                .message("Đã kết thúc phiên hội thoại")
                .build();
    }

    @PostMapping("/reviews")
    public ApiResponse<AiReviewResponse> submitReview(@Valid @RequestBody AiReviewCreateRequest request) {
        AiReviewResponse response = aiTutorService.submitReview(request);
        return ApiResponse.<AiReviewResponse>builder()
                .result(response)
                .message("Gửi đánh giá chất lượng phản hồi AI thành công")
                .build();
    }

    // --- AI ROLEPLAY (UC-104, UC-105, UC-106) ---

    @GetMapping("/scenarios")
    public ApiResponse<List<AiScenarioResponse>> getScenarios() {
        List<AiScenarioResponse> response = aiRoleplayService.getActiveScenarios();
        return ApiResponse.<List<AiScenarioResponse>>builder()
                .result(response)
                .build();
    }

    @GetMapping("/scenarios/{id}")
    public ApiResponse<AiScenarioResponse> getScenarioDetail(@PathVariable String id) {
        AiScenarioResponse response = aiRoleplayService.getScenarioDetail(id);
        return ApiResponse.<AiScenarioResponse>builder()
                .result(response)
                .build();
    }

    @PostMapping("/roleplay/start/{scenarioId}")
    public ApiResponse<AiSessionResponse> startRoleplay(@PathVariable String scenarioId) {
        AiSessionResponse response = aiRoleplayService.startRoleplay(scenarioId);
        return ApiResponse.<AiSessionResponse>builder()
                .result(response)
                .message("Bắt đầu kịch bản Roleplay thành công")
                .build();
    }

    @PostMapping("/roleplay/sessions/{id}/messages")
    public ApiResponse<AiMessageResponse> sendRoleplayMessage(
            @PathVariable String id,
            @Valid @RequestBody AiMessageSendRequest request
    ) {
        AiMessageResponse response = aiRoleplayService.sendRoleplayMessage(id, request);
        return ApiResponse.<AiMessageResponse>builder()
                .result(response)
                .build();
    }

    @PostMapping("/roleplay/sessions/{id}/finish")
    public ApiResponse<Map<String, Object>> finishRoleplay(@PathVariable String id) {
        Map<String, Object> response = aiRoleplayService.finishRoleplay(id);
        return ApiResponse.<Map<String, Object>>builder()
                .result(response)
                .message("Hoàn thành kịch bản Roleplay")
                .build();
    }

    @PostMapping("/roleplay/scenarios/{scenarioId}/complete")
    public ApiResponse<Map<String, Object>> completeScenario(
            @PathVariable String scenarioId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        Integer score = null;
        if (body != null && body.containsKey("score")) {
            try {
                score = Integer.parseInt(body.get("score").toString());
            } catch (Exception ignored) {}
        }
        Map<String, Object> response = aiRoleplayService.completeScenario(scenarioId, score);
        return ApiResponse.<Map<String, Object>>builder()
                .result(response)
                .message("Ghi nhận hoàn thành kịch bản thành công")
                .build();
    }
}
