package com.sep490.backend.controller;

import com.sep490.backend.dto.request.BadgeRequest;
import com.sep490.backend.dto.request.RoleRequest;
import com.sep490.backend.dto.request.SystemSettingRequest;
import com.sep490.backend.dto.request.XpRuleRequest;
import com.sep490.backend.dto.response.*;
import com.sep490.backend.service.AdminService;
import jakarta.validation.Valid;
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
    public ApiResponse<AdminUserResponse> getUserById(@PathVariable("id") Long id) {
        return ApiResponse.<AdminUserResponse>builder()
                .result(adminService.getUserById(id))
                .build();
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<AdminUserResponse> toggleUserStatus(
            @PathVariable("id") Long id,
            @RequestParam("status") String status) {
        return ApiResponse.<AdminUserResponse>builder()
                .result(adminService.toggleUserStatus(id, status))
                .message("User status updated")
                .build();
    }

    @PutMapping("/users/{id}/roles")
    public ApiResponse<AdminUserResponse> updateUserRoles(
            @PathVariable("id") Long id,
            @RequestBody Set<String> roles) {
        return ApiResponse.<AdminUserResponse>builder()
                .result(adminService.updateUserRoles(id, roles))
                .message("User roles updated")
                .build();
    }

    @GetMapping("/roles")
    public ApiResponse<List<RoleResponse>> getRoles() {
        return ApiResponse.<List<RoleResponse>>builder()
                .result(adminService.getRoles())
                .build();
    }

    @PostMapping("/roles")
    public ApiResponse<RoleResponse> createRole(@Valid @RequestBody RoleRequest request) {
        return ApiResponse.<RoleResponse>builder()
                .result(adminService.createRole(request))
                .message("Role created")
                .build();
    }

    @PutMapping("/roles/{id}")
    public ApiResponse<RoleResponse> updateRole(
            @PathVariable("id") Integer id,
            @Valid @RequestBody RoleRequest request) {
        return ApiResponse.<RoleResponse>builder()
                .result(adminService.updateRole(id, request))
                .message("Role updated")
                .build();
    }

    @DeleteMapping("/roles/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable("id") Integer id) {
        adminService.deleteRole(id);
        return ApiResponse.<Void>builder()
                .message("Role deleted")
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
    public ApiResponse<Void> deleteSetting(@PathVariable("id") Long id) {
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

    @PostMapping("/achievements")
    public ApiResponse<BadgeResponse> createBadge(@Valid @RequestBody BadgeRequest request) {
        return ApiResponse.<BadgeResponse>builder()
                .result(adminService.createAchievement(request))
                .message("Achievement created")
                .build();
    }

    @PutMapping("/achievements/{id}")
    public ApiResponse<BadgeResponse> updateBadge(
            @PathVariable("id") Long id,
            @Valid @RequestBody BadgeRequest request) {
        return ApiResponse.<BadgeResponse>builder()
                .result(adminService.updateAchievement(id, request))
                .message("Achievement updated")
                .build();
    }

    @DeleteMapping("/achievements/{id}")
    public ApiResponse<Void> deleteBadge(@PathVariable("id") Long id) {
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

    @PostMapping("/xp-rules")
    public ApiResponse<XpRuleResponse> createXpRule(@Valid @RequestBody XpRuleRequest request) {
        return ApiResponse.<XpRuleResponse>builder()
                .result(adminService.createXpRule(request))
                .message("XP rule created")
                .build();
    }

    @PutMapping("/xp-rules/{id}")
    public ApiResponse<XpRuleResponse> updateXpRule(
            @PathVariable("id") Long id,
            @Valid @RequestBody XpRuleRequest request) {
        return ApiResponse.<XpRuleResponse>builder()
                .result(adminService.updateXpRule(id, request))
                .message("XP rule updated")
                .build();
    }

    @DeleteMapping("/xp-rules/{id}")
    public ApiResponse<Void> deleteXpRule(@PathVariable("id") Long id) {
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
