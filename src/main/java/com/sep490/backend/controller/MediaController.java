package com.sep490.backend.controller;

import com.sep490.backend.dto.response.ApiResponse;
import com.sep490.backend.dto.response.CloudinaryUploadSignatureResponse;
import com.sep490.backend.service.CloudinarySignatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final CloudinarySignatureService cloudinarySignatureService;

    /**
     * Tạo signature upload audio lên Cloudinary (lưu vào folder riêng: hola-vietnamese/audio).
     * POST /api/media/audio/signature?prefix=vocab-lesson1
     */
    @PostMapping("/audio/signature")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CloudinaryUploadSignatureResponse> createAudioUploadSignature(
            @RequestParam(name = "prefix", required = false, defaultValue = "audio") String prefix) {
        CloudinaryUploadSignatureResponse response = cloudinarySignatureService.createAudioSignature(prefix);
        return ApiResponse.<CloudinaryUploadSignatureResponse>builder()
                .code(1000)
                .result(response)
                .build();
    }
}
