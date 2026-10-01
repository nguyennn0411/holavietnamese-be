package com.sep490.backend.controller;

import com.nimbusds.jose.JOSEException;
import com.sep490.backend.dto.request.AuthenticationRequest;
import com.sep490.backend.dto.request.IntrospectRequest;
import com.sep490.backend.dto.request.LogoutRequest;
import com.sep490.backend.dto.request.RefreshRequest;
import com.sep490.backend.dto.response.ApiResponse;
import com.sep490.backend.dto.response.AuthenticationResponse;
import com.sep490.backend.dto.response.IntrospectResponse;
import com.sep490.backend.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import com.sep490.backend.dto.request.UserRegisterRequest;
import com.sep490.backend.dto.response.UserResponse;
import com.sep490.backend.service.UserService;

import java.text.ParseException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AuthController {

    AuthenticationService authenticationService;
    UserService userService;

    /**
     * Đăng ký tài khoản học viên mới.
     *
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ApiResponse<UserResponse> register(
            @Valid @RequestBody UserRegisterRequest request) {
        UserResponse result = userService.register(request);
        return ApiResponse.<UserResponse>builder()
                .code(1000)
                .result(result)
                .message("Đăng ký tài khoản thành công")
                .build();
    }

    /**
     * Đăng nhập - xác thực tài khoản và trả về JWT token cùng thông tin người dùng.
     *
     * POST /api/auth/token
     * Body: { "username": "...", "password": "..." }
     */
    @PostMapping("/token")
    public ApiResponse<AuthenticationResponse> authenticate(
            @Valid @RequestBody AuthenticationRequest request) {
        var result = authenticationService.authenticate(request);
        return ApiResponse.<AuthenticationResponse>builder()
                .result(result)
                .message("Đăng nhập thành công")
                .build();
    }

    /**
     * Đăng nhập bằng Google (Google ID Token / Credential).
     *
     * POST /api/auth/google
     * Body: { "credential": "eyJ..." }
     */
    @PostMapping("/google")
    public ApiResponse<AuthenticationResponse> authenticateGoogle(
            @Valid @RequestBody com.sep490.backend.dto.request.GoogleLoginRequest request) {
        var result = authenticationService.authenticateGoogle(request);
        return ApiResponse.<AuthenticationResponse>builder()
                .code(1000)
                .result(result)
                .message("Đăng nhập Google thành công")
                .build();
    }

    /**
     * Kiểm tra token có hợp lệ không (Introspect).
     *
     * POST /api/auth/introspect
     * Body: { "token": "eyJ..." }
     */
    @PostMapping("/introspect")
    public ApiResponse<IntrospectResponse> introspect(
            @Valid @RequestBody IntrospectRequest request) throws ParseException, JOSEException {
        var result = authenticationService.introspect(request);
        return ApiResponse.<IntrospectResponse>builder().result(result).build();
    }

    /**
     * Làm mới token (Refresh token).
     *
     * POST /api/auth/refresh
     * Body: { "token": "eyJ..." }
     */
    @PostMapping("/refresh")
    public ApiResponse<AuthenticationResponse> refreshToken(
            @Valid @RequestBody RefreshRequest request) throws ParseException, JOSEException {
        var result = authenticationService.refreshToken(request);
        return ApiResponse.<AuthenticationResponse>builder()
                .result(result)
                .message("Làm mới token thành công")
                .build();
    }

    /**
     * Đăng xuất - đưa token vào blacklist.
     * Hỗ trợ lấy token từ Body { "token": "..." } hoặc Header "Authorization: Bearer <token>".
     *
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestBody(required = false) LogoutRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpServletRequest) {

        String token = null;
        if (request != null && request.getToken() != null && !request.getToken().isBlank()) {
            token = request.getToken();
        } else if (authHeader != null && !authHeader.isBlank()) {
            token = authHeader;
        }

        if (token != null) {
            authenticationService.logout(token);
        }

        return ApiResponse.<Void>builder()
                .message("Đăng xuất thành công")
                .build();
    }
}
