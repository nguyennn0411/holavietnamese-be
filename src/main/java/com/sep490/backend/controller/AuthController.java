package com.sep490.backend.controller;

import com.nimbusds.jose.JOSEException;
import com.sep490.backend.dto.request.*;
import com.sep490.backend.dto.response.ApiResponse;
import com.sep490.backend.dto.response.AuthenticationResponse;
import com.sep490.backend.dto.response.IntrospectResponse;
import com.sep490.backend.dto.response.UserResponse;
import com.sep490.backend.service.AuthenticationService;
import com.sep490.backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AuthController {

    AuthenticationService authenticationService;
    UserService userService;

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

    @PostMapping("/token")
    public ApiResponse<AuthenticationResponse> authenticate(
            @Valid @RequestBody AuthenticationRequest request) {
        var result = authenticationService.authenticate(request);
        return ApiResponse.<AuthenticationResponse>builder()
                .result(result)
                .message("Đăng nhập thành công")
                .build();
    }

    @PostMapping("/google")
    public ApiResponse<AuthenticationResponse> authenticateGoogle(
            @Valid @RequestBody GoogleLoginRequest request) {
        var result = authenticationService.authenticateGoogle(request);
        return ApiResponse.<AuthenticationResponse>builder()
                .code(1000)
                .result(result)
                .message("Đăng nhập Google thành công")
                .build();
    }

    @GetMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@RequestParam("token") String token) {
        authenticationService.verifyEmail(token);
        return ApiResponse.<Void>builder()
                .code(1000)
                .message("Xác minh email thành công")
                .build();
    }

    @PostMapping("/verify-email/resend")
    public ApiResponse<String> resendVerifyEmail(@Valid @RequestBody ForgotPasswordInitiateRequest request) {
        String token = authenticationService.resendVerificationEmail(request.getEmail());
        return ApiResponse.<String>builder()
                .code(1000)
                .result(token)
                .message(token == null ? "Email đã được xác minh" : "Đã tạo lại liên kết xác minh email")
                .build();
    }

    @PostMapping("/forgot-password/initiate")
    public ApiResponse<String> initiateForgotPassword(@Valid @RequestBody ForgotPasswordInitiateRequest request) {
        String otp = authenticationService.initiateForgotPassword(request.getEmail());
        return ApiResponse.<String>builder()
                .code(1000)
                .result(otp)
                .message("Đã gửi mã OTP khôi phục mật khẩu đến email")
                .build();
    }

    @PostMapping("/forgot-password/verify-otp")
    public ApiResponse<Boolean> verifyForgotPasswordOtp(@Valid @RequestBody VerifyOtpRequest request) {
        boolean valid = authenticationService.verifyForgotPasswordOtp(request.getEmail(), request.getOtp());
        return ApiResponse.<Boolean>builder()
                .code(1000)
                .result(valid)
                .message(valid ? "Mã OTP hợp lệ" : "Mã OTP không hợp lệ hoặc đã hết hạn")
                .build();
    }

    @PostMapping("/forgot-password/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authenticationService.resetPasswordWithOtp(request);
        return ApiResponse.<Void>builder()
                .code(1000)
                .message("Đặt lại mật khẩu thành công")
                .build();
    }

    @PostMapping("/introspect")
    public ApiResponse<IntrospectResponse> introspect(
            @Valid @RequestBody IntrospectRequest request) throws ParseException, JOSEException {
        var result = authenticationService.introspect(request);
        return ApiResponse.<IntrospectResponse>builder().result(result).build();
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthenticationResponse> refreshToken(
            @Valid @RequestBody RefreshRequest request) throws ParseException, JOSEException {
        var result = authenticationService.refreshToken(request);
        return ApiResponse.<AuthenticationResponse>builder()
                .result(result)
                .message("Làm mới token thành công")
                .build();
    }

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
