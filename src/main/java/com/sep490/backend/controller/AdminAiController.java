package com.sep490.backend.controller;

import com.sep490.backend.dto.request.AiScenarioRequest;
import com.sep490.backend.dto.request.AiSettingUpdateRequest;
import com.sep490.backend.dto.response.*;
import com.sep490.backend.service.AdminAiService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiController {

    AdminAiService adminAiService;

    // --- AI SETTINGS (UC-107) ---

    @GetMapping("/settings")
    public ApiResponse<AiSettingResponse> getSettings() {
        AiSettingResponse response = adminAiService.getAiSettings();
        return ApiResponse.<AiSettingResponse>builder()
                .result(response)
                .build();
    }

    @PutMapping("/settings")
    public ApiResponse<AiSettingResponse> updateSettings(@Valid @RequestBody AiSettingUpdateRequest request) {
        AiSettingResponse response = adminAiService.updateAiSettings(request);
        return ApiResponse.<AiSettingResponse>builder()
                .result(response)
                .message("Cập nhật cấu hình AI thành công")
                .build();
    }

    // --- AI USAGE STATS (UC-108) ---

    @GetMapping("/usage")
    public ApiResponse<AiUsageStatsResponse> getUsageStats() {
        AiUsageStatsResponse response = adminAiService.getUsageStatistics();
        return ApiResponse.<AiUsageStatsResponse>builder()
                .result(response)
                .build();
    }

    // --- AI SCENARIOS BUILDER ---

    @GetMapping("/scenarios")
    public ApiResponse<List<AiScenarioResponse>> getAllScenarios() {
        List<AiScenarioResponse> response = adminAiService.getAllScenarios();
        return ApiResponse.<List<AiScenarioResponse>>builder()
                .result(response)
                .build();
    }

    @PostMapping("/scenarios")
    public ApiResponse<AiScenarioResponse> saveScenario(@Valid @RequestBody AiScenarioRequest request) {
        AiScenarioResponse response = adminAiService.saveScenario(request);
        return ApiResponse.<AiScenarioResponse>builder()
                .result(response)
                .message("Lưu kịch bản AI Roleplay thành công")
                .build();
    }

    @DeleteMapping("/scenarios/{id}")
    public ApiResponse<Void> deleteScenario(@PathVariable String id) {
        adminAiService.deleteScenario(id);
        return ApiResponse.<Void>builder()
                .message("Đã xóa kịch bản")
                .build();
    }

    @PatchMapping("/scenarios/{id}/status")
    public ApiResponse<AiScenarioResponse> updateScenarioStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body
    ) {
        String status = body.getOrDefault("status", "ACTIVE");
        AiScenarioResponse response = adminAiService.updateScenarioStatus(id, status);
        return ApiResponse.<AiScenarioResponse>builder()
                .result(response)
                .message("Cập nhật trạng thái kịch bản thành công")
                .build();
    }

    // --- AI REVIEWS & QUALITY AUDIT ---

    @GetMapping("/reviews")
    public ApiResponse<List<AiReviewResponse>> getReviews() {
        List<AiReviewResponse> response = adminAiService.getReviews();
        return ApiResponse.<List<AiReviewResponse>>builder()
                .result(response)
                .build();
    }

    @PatchMapping("/reviews/{id}")
    public ApiResponse<AiReviewResponse> updateReviewStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body
    ) {
        String status = body.getOrDefault("status", "APPROVED");
        String adminNote = body.getOrDefault("adminNote", "");
        AiReviewResponse response = adminAiService.updateReviewStatus(id, status, adminNote);
        return ApiResponse.<AiReviewResponse>builder()
                .result(response)
                .message("Đã kiểm duyệt đánh giá AI")
                .build();
    }
}
