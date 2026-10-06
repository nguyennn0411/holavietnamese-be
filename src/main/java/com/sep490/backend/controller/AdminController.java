package com.sep490.backend.controller;

import com.sep490.backend.dto.response.*;
import com.sep490.backend.entity.Badge;
import com.sep490.backend.service.AchievementService;
import com.sep490.backend.service.AdminService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminController {

    AdminService adminService;
    AchievementService achievementService;

    @GetMapping("/dashboard/stats")
    public ApiResponse<AdminDashboardStatsResponse> getDashboardStats() {
        AdminDashboardStatsResponse stats = adminService.getDashboardStats();
        return ApiResponse.<AdminDashboardStatsResponse>builder()
                .result(stats)
                .build();
    }

    @GetMapping("/users")
    public ApiResponse<List<AdminUserResponse>> getAllUsers() {
        List<AdminUserResponse> users = adminService.getAllUsers();
        return ApiResponse.<List<AdminUserResponse>>builder()
                .result(users)
                .build();
    }

    @GetMapping("/users/{id}")
    public ApiResponse<AdminUserResponse> getUserById(@PathVariable("id") Long id) {
        AdminUserResponse user = adminService.getUserById(id);
        return ApiResponse.<AdminUserResponse>builder()
                .result(user)
                .build();
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<AdminUserResponse> toggleUserStatus(@PathVariable("id") Long id, @RequestParam("status") String status) {
        AdminUserResponse user = adminService.toggleUserStatus(id, status);
        return ApiResponse.<AdminUserResponse>builder()
                .result(user)
                .message("Đã cập nhật trạng thái người dùng")
                .build();
    }

    @PutMapping("/users/{id}/roles")
    public ApiResponse<AdminUserResponse> updateUserRoles(@PathVariable("id") Long id, @RequestBody Set<String> roles) {
        AdminUserResponse user = adminService.updateUserRoles(id, roles);
        return ApiResponse.<AdminUserResponse>builder()
                .result(user)
                .message("Đã cập nhật vai trò người dùng")
                .build();
    }

    @PostMapping("/achievements")
    public ApiResponse<Badge> createBadge(@RequestBody Badge badge) {
        Badge result = achievementService.createBadge(badge);
        return ApiResponse.<Badge>builder()
                .result(result)
                .message("Tạo huy hiệu thành công")
                .build();
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<AuditLogResponse>> getAuditLogs() {
        List<AuditLogResponse> logs = adminService.getAuditLogs();
        return ApiResponse.<List<AuditLogResponse>>builder()
                .result(logs)
                .build();
    }
}
