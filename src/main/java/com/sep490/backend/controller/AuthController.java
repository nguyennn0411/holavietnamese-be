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

    /**
     * Đăng nhập - xác thực tài khoản và trả về JWT token.
     *
     * POST /api/auth/token
     * Body: { "username": "...", "password": "..." }
     */
    @PostMapping("/token")
    public ApiResponse<AuthenticationResponse> authenticate(
            @Valid @RequestBody AuthenticationRequest request) {
        var result = authenticationService.authenticate(request);
        log.info("User [{}] đăng nhập thành công", request.getUsername());
        return ApiResponse.<AuthenticationResponse>builder()
                .result(result)
                .message("Đăng nhập thành công")
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
     *
     * POST /api/auth/logout
     * Body: { "token": "eyJ..." }
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestBody(required = false) LogoutRequest request) throws ParseException, JOSEException {
        if (request != null) {
            authenticationService.logout(request);
        }
        return ApiResponse.<Void>builder().message("Đăng xuất thành công").build();
    }
}
