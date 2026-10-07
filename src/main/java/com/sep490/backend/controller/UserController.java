package com.sep490.backend.controller;

import com.sep490.backend.dto.request.*;
import com.sep490.backend.dto.response.*;
import com.sep490.backend.service.AchievementService;
import com.sep490.backend.service.NotificationService;
import com.sep490.backend.service.UserProgressService;
import com.sep490.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserController {

    UserService userService;
    UserProgressService userProgressService;
    AchievementService achievementService;
    NotificationService notificationService;

    @PostMapping("/register")
    public ApiResponse<UserResponse> register(@Valid @RequestBody UserRegisterRequest request) {
        UserResponse response = userService.register(request);
        return ApiResponse.<UserResponse>builder()
                .result(response)
                .message("Đăng ký tài khoản học viên thành công")
                .build();
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyProfile() {
        UserResponse response = userService.getMyProfile();
        return ApiResponse.<UserResponse>builder()
                .result(response)
                .build();
    }

    @PutMapping("/me")
    public ApiResponse<UserResponse> updateProfile(@Valid @RequestBody UserUpdateRequest request) {
        UserResponse response = userService.updateProfile(request);
        return ApiResponse.<UserResponse>builder()
                .result(response)
                .message("Cập nhật thông tin thành công")
                .build();
    }

    @PostMapping("/onboarding")
    public ApiResponse<UserResponse> completeOnboarding(@Valid @RequestBody OnboardingRequest request) {
        UserResponse response = userProgressService.completeOnboarding(request);
        return ApiResponse.<UserResponse>builder()
                .result(response)
                .message("Hoàn thành Onboarding thành công")
                .build();
    }

    @PutMapping("/settings")
    public ApiResponse<UserResponse> updateSettings(@RequestBody UserSettingsRequest request) {
        UserResponse response = userProgressService.updateUserSettings(request);
        return ApiResponse.<UserResponse>builder()
                .result(response)
                .message("Cập nhật cài đặt thành công")
                .build();
    }

    @GetMapping("/progress/summary")
    public ApiResponse<UserProgressResponse> getUserProgress() {
        UserProgressResponse response = userProgressService.getUserProgress();
        return ApiResponse.<UserProgressResponse>builder()
                .result(response)
                .build();
    }

    @GetMapping("/achievements")
    public ApiResponse<List<BadgeResponse>> getUserAchievements() {
        List<BadgeResponse> response = achievementService.getUserBadges();
        return ApiResponse.<List<BadgeResponse>>builder()
                .result(response)
                .build();
    }

    @GetMapping("/notifications")
    public ApiResponse<List<NotificationResponse>> getNotifications() {
        List<NotificationResponse> response = notificationService.getUserNotifications();
        return ApiResponse.<List<NotificationResponse>>builder()
                .result(response)
                .build();
    }

    @PutMapping("/notifications/{id}/read")
    public ApiResponse<Void> markNotificationAsRead(@PathVariable("id") Long id) {
        notificationService.markAsRead(id);
        return ApiResponse.<Void>builder()
                .message("Đã đánh dấu thông báo là đã đọc")
                .build();
    }

    @PutMapping("/notifications/read-all")
    public ApiResponse<Void> markAllNotificationsAsRead() {
        notificationService.markAllAsRead();
        return ApiResponse.<Void>builder()
                .message("Đã đánh dấu tất cả thông báo là đã đọc")
                .build();
    }

    @PutMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return ApiResponse.<Void>builder()
                .message("Đổi mật khẩu thành công")
                .build();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<UserResponse>> getAllUsers() {
        List<UserResponse> response = userService.getAllUsers();
        return ApiResponse.<List<UserResponse>>builder()
                .result(response)
                .build();
    }
}
