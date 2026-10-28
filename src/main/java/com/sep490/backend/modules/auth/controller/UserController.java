package com.sep490.backend.modules.auth.controller;

import com.sep490.backend.modules.auth.dto.request.AvatarUrlRequest;
import com.sep490.backend.modules.auth.dto.request.ChangePasswordRequest;
import com.sep490.backend.modules.auth.dto.request.OnboardingRequest;
import com.sep490.backend.modules.auth.dto.request.UserRegisterRequest;
import com.sep490.backend.modules.auth.dto.request.UserSettingsRequest;
import com.sep490.backend.modules.auth.dto.request.UserUpdateRequest;
import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.common.dto.CloudinaryUploadSignatureResponse;
import com.sep490.backend.modules.auth.dto.response.NotificationResponse;
import com.sep490.backend.modules.auth.dto.response.UserResponse;
import com.sep490.backend.modules.auth.dto.response.BadgeResponse;
import com.sep490.backend.modules.auth.dto.response.UserProgressResponse;
import com.sep490.backend.modules.auth.port.LearnerProfileUseCase;
import com.sep490.backend.modules.auth.service.NotificationService;
import com.sep490.backend.modules.auth.service.UserService;
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
    LearnerProfileUseCase learnerProfile;
    NotificationService notificationService;

    @PostMapping("/register")
    public ApiResponse<UserResponse> register(@Valid @RequestBody UserRegisterRequest request) {
        UserResponse response = userService.register(request);
        return ApiResponse.<UserResponse>builder()
                .result(response)
                .message("Đăng ký thành công. Vui lòng kiểm tra email để lấy mã OTP")
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

    @PostMapping("/me/avatar/signature")
    public ApiResponse<CloudinaryUploadSignatureResponse> createProfileAvatarUploadSignature() {
        CloudinaryUploadSignatureResponse response = userService.createProfileAvatarUploadSignature();
        return ApiResponse.<CloudinaryUploadSignatureResponse>builder()
                .result(response)
                .build();
    }

    @PutMapping("/me/avatar")
    public ApiResponse<UserResponse> updateProfileAvatarUrl(@Valid @RequestBody AvatarUrlRequest request) {
        UserResponse response = userService.updateProfileAvatarUrl(request);
        return ApiResponse.<UserResponse>builder()
                .result(response)
                .message("Cập nhật ảnh đại diện thành công")
                .build();
    }

    @PostMapping("/onboarding")
    public ApiResponse<UserResponse> completeOnboarding(@Valid @RequestBody OnboardingRequest request) {
        UserResponse response = learnerProfile.completeOnboarding(request);
        return ApiResponse.<UserResponse>builder()
                .result(response)
                .message("Hoàn thành Onboarding thành công")
                .build();
    }

    @GetMapping("/settings")
    public ApiResponse<UserResponse> getSettings() {
        UserResponse response = userService.getMyProfile();
        return ApiResponse.<UserResponse>builder()
                .result(response)
                .build();
    }

    @PutMapping("/settings")
    public ApiResponse<UserResponse> updateSettings(@RequestBody UserSettingsRequest request) {
        UserResponse response = learnerProfile.updateUserSettings(request);
        return ApiResponse.<UserResponse>builder()
                .result(response)
                .message("Cập nhật cài đặt thành công")
                .build();
    }

    @GetMapping("/progress/summary")
    public ApiResponse<UserProgressResponse> getUserProgress() {
        UserProgressResponse response = learnerProfile.getUserProgress();
        return ApiResponse.<UserProgressResponse>builder()
                .result(response)
                .build();
    }

    @GetMapping("/learning-overview")
    public ApiResponse<UserProgressResponse> getLearningOverview() {
        return getUserProgress();
    }

    @GetMapping("/achievements")
    public ApiResponse<List<BadgeResponse>> getUserAchievements() {
        List<BadgeResponse> response = learnerProfile.getUserBadges();
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
