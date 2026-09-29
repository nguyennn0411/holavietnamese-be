package com.sep490.backend.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
@RestController
public class SessionController {
    @GetMapping("/api/me/session")
    public Map<String, Object> current(@org.springframework.security.core.annotation.AuthenticationPrincipal com.sep490.backend.config.LearnerPrincipal user) {
        return Map.of("id", user.id(), "email", user.email());
    }
    @GetMapping("/api/csrf") public Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken(), "headerName", token.getHeaderName()); }
}
