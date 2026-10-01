package com.sep490.backend.controller;

import com.sep490.backend.dto.request.ChangePasswordRequest;
import com.sep490.backend.dto.request.UserRegisterRequest;
import com.sep490.backend.dto.request.UserUpdateRequest;
import com.sep490.backend.dto.response.ApiResponse;
import com.sep490.backend.dto.response.UserResponse;
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

    /**
     * Đăng ký tài khoản học viên mới (Public).
     * POST /api/users/register
     */
    @PostMapping("/register")
    public ApiResponse<UserResponse> register(@Valid @RequestBody UserRegisterRequest request) {
        UserResponse response = userService.register(request);
        return ApiResponse.<UserResponse>builder()
                .result(response)
                .message("Đăng ký tài khoản học viên thành công")
                .build();
    }

    /**
     * Lấy thông tin cá nhân của người dùng hiện tại (Secured).
     * GET /api/users/me
     */
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyProfile() {
        UserResponse response = userService.getMyProfile();
        return ApiResponse.<UserResponse>builder()
                .result(response)
                .build();
    }

    /**
     * Cập nhật thông tin học tập và cá nhân (Secured).
     * PUT /api/users/me
     */
    @PutMapping("/me")
    public ApiResponse<UserResponse> updateProfile(@Valid @RequestBody UserUpdateRequest request) {
        UserResponse response = userService.updateProfile(request);
        return ApiResponse.<UserResponse>builder()
                .result(response)
                .message("Cập nhật thông tin thành công")
                .build();
    }

    /**
     * Đổi mật khẩu (Secured).
     * PUT /api/users/change-password
     */
    @PutMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return ApiResponse.<Void>builder()
                .message("Đổi mật khẩu thành công")
                .build();
    }

    /**
     * Lấy danh sách toàn bộ người dùng (Dành cho Admin).
     * GET /api/users
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<UserResponse>> getAllUsers() {
        List<UserResponse> response = userService.getAllUsers();
        return ApiResponse.<List<UserResponse>>builder()
                .result(response)
                .build();
    }
}
