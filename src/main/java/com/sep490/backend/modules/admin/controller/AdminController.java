package com.sep490.backend.modules.admin.controller;

import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.common.dto.PageResponse;
import com.sep490.backend.modules.admin.dto.request.BadgeRequest;
import com.sep490.backend.modules.admin.dto.request.SystemSettingRequest;
import com.sep490.backend.modules.admin.dto.request.UpdateUserRoleRequest;
import com.sep490.backend.modules.admin.dto.request.XpRuleRequest;
import com.sep490.backend.modules.admin.dto.response.AdminDashboardStatsResponse;
import com.sep490.backend.modules.admin.dto.response.AdminUserResponse;
import com.sep490.backend.modules.admin.dto.response.AuditLogResponse;
import com.sep490.backend.modules.admin.dto.response.RoleResponse;
import com.sep490.backend.modules.admin.dto.response.SystemSettingResponse;
import com.sep490.backend.modules.admin.dto.response.XpRuleResponse;
import com.sep490.backend.modules.admin.service.AdminService;
import com.sep490.backend.modules.auth.dto.response.BadgeResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAnyRole('ADMIN', 'CONTENT')")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminController {

    AdminService adminService;

    @GetMapping("/dashboard/stats")
    public ApiResponse<AdminDashboardStatsResponse> getDashboardStats() {
        return ApiResponse.<AdminDashboardStatsResponse>builder()
                .result(adminService.getDashboardStats())
                .build();
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserResponse>> getAllUsers(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.<PageResponse<AdminUserResponse>>builder()
                .result(adminService.searchUsers(search, status, role, page, size))
                .build();
    }

    @GetMapping("/users/{id}")
    public ApiResponse<AdminUserResponse> getUserById(@PathVariable Long id) {
        return ApiResponse.<AdminUserResponse>builder()
                .result(adminService.getUserById(id))
                .build();
    }

    @PatchMapping("/users/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminUserResponse> toggleUserStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return ApiResponse.<AdminUserResponse>builder()
                .result(adminService.toggleUserStatus(id, status))
                .message("User status updated")
                .build();
    }

    /**
     * The sole role mutation endpoint. Its singular path and request DTO make
     * a multi-role request impossible.
     */
    @PutMapping({"/users/{id}/role", "/users/{id}/roles"})
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AdminUserResponse> updateUserRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRoleRequest request) {
        return ApiResponse.<AdminUserResponse>builder()
                .result(adminService.updateUserRole(id, request.getRole()))
                .message("User role updated")
                .build();
    }

    /** Fixed system role metadata; mutations are intentionally not exposed. */
    @GetMapping("/roles")
    public ApiResponse<List<RoleResponse>> getRoles() {
        return ApiResponse.<List<RoleResponse>>builder()
                .result(adminService.getRoles())
                .build();
    }

    @GetMapping("/settings")
    public ApiResponse<List<SystemSettingResponse>> getSettings() {
        return ApiResponse.<List<SystemSettingResponse>>builder()
                .result(adminService.getSettings())
                .build();
    }

    @PutMapping("/settings")
    public ApiResponse<SystemSettingResponse> upsertSetting(@Valid @RequestBody SystemSettingRequest request) {
        return ApiResponse.<SystemSettingResponse>builder()
                .result(adminService.upsertSetting(request))
                .message("Setting saved")
                .build();
    }

    @DeleteMapping("/settings/{id}")
    public ApiResponse<Void> deleteSetting(@PathVariable Long id) {
        adminService.deleteSetting(id);
        return ApiResponse.<Void>builder()
                .message("Setting deleted")
                .build();
    }

    @GetMapping("/achievements")
    public ApiResponse<List<BadgeResponse>> getAchievements() {
        return ApiResponse.<List<BadgeResponse>>builder()
                .result(adminService.getAchievements())
                .build();
    }

    @org.springframework.web.bind.annotation.PostMapping("/achievements")
    public ApiResponse<BadgeResponse> createBadge(@Valid @RequestBody BadgeRequest request) {
        return ApiResponse.<BadgeResponse>builder()
                .result(adminService.createAchievement(request))
                .message("Achievement created")
                .build();
    }

    @PutMapping("/achievements/{id}")
    public ApiResponse<BadgeResponse> updateBadge(@PathVariable Long id, @Valid @RequestBody BadgeRequest request) {
        return ApiResponse.<BadgeResponse>builder()
                .result(adminService.updateAchievement(id, request))
                .message("Achievement updated")
                .build();
    }

    @DeleteMapping("/achievements/{id}")
    public ApiResponse<Void> deleteBadge(@PathVariable Long id) {
        adminService.deleteAchievement(id);
        return ApiResponse.<Void>builder()
                .message("Achievement deleted")
                .build();
    }

    @GetMapping("/xp-rules")
    public ApiResponse<List<XpRuleResponse>> getXpRules() {
        return ApiResponse.<List<XpRuleResponse>>builder()
                .result(adminService.getXpRules())
                .build();
    }

    @org.springframework.web.bind.annotation.PostMapping("/xp-rules")
    public ApiResponse<XpRuleResponse> createXpRule(@Valid @RequestBody XpRuleRequest request) {
        return ApiResponse.<XpRuleResponse>builder()
                .result(adminService.createXpRule(request))
                .message("XP rule created")
                .build();
    }

    @PutMapping("/xp-rules/{id}")
    public ApiResponse<XpRuleResponse> updateXpRule(@PathVariable Long id, @Valid @RequestBody XpRuleRequest request) {
        return ApiResponse.<XpRuleResponse>builder()
                .result(adminService.updateXpRule(id, request))
                .message("XP rule updated")
                .build();
    }

    @DeleteMapping("/xp-rules/{id}")
    public ApiResponse<Void> deleteXpRule(@PathVariable Long id) {
        adminService.deleteXpRule(id);
        return ApiResponse.<Void>builder()
                .message("XP rule deleted")
                .build();
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<AuditLogResponse>> getAuditLogs() {
        return ApiResponse.<List<AuditLogResponse>>builder()
                .result(adminService.getAuditLogs())
                .build();
    }
}
