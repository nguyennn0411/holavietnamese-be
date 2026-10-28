package com.sep490.backend.modules.admin.media.controller;

import com.sep490.backend.common.dto.ApiResponse;
import com.sep490.backend.common.dto.CloudinaryUploadSignatureResponse;
import com.sep490.backend.common.media.CloudinarySignatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final CloudinarySignatureService cloudinarySignatureService;

    @PostMapping("/courses/cover/signature")
    @PreAuthorize("hasAnyRole('ADMIN','CONTENT_MANAGER')")
    public ApiResponse<CloudinaryUploadSignatureResponse> createCourseCoverUploadSignature() {
        return ApiResponse.<CloudinaryUploadSignatureResponse>builder()
                .code(1000)
                .result(cloudinarySignatureService.createCourseCoverSignature())
                .build();
    }

    @PostMapping("/lessons/signature")
    @PreAuthorize("hasAnyRole('ADMIN','CONTENT_MANAGER')")
    public ApiResponse<CloudinaryUploadSignatureResponse> createLessonMediaUploadSignature(
            @RequestParam String type) {
        return ApiResponse.<CloudinaryUploadSignatureResponse>builder()
                .code(1000)
                .result(cloudinarySignatureService.createLessonMediaSignature(type))
                .build();
    }

    @PostMapping("/questions/signature")
    @PreAuthorize("hasAnyRole('ADMIN','CONTENT_MANAGER')")
    public ApiResponse<CloudinaryUploadSignatureResponse> createQuestionMediaUploadSignature(
            @RequestParam String type) {
        return ApiResponse.<CloudinaryUploadSignatureResponse>builder()
                .code(1000)
                .result(cloudinarySignatureService.createQuestionMediaSignature(type))
                .build();
    }

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
