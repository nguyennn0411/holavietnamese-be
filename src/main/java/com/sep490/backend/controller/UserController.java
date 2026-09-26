package com.sep490.backend.controller;

import com.sep490.backend.dto.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/me")
    public ApiResponse<String> getMyProfile() {
        return ApiResponse.<String>builder()
                .result("This is a secured endpoint")
                .build();
    }
    
    @GetMapping
    public ApiResponse<String> getPublicUsers() {
        return ApiResponse.<String>builder()
                .result("This is a public endpoint")
                .build();
    }
}
