package com.sep490.backend.modules.learning.progress.web.dto;
import com.sep490.backend.modules.learning.progress.domain.EnrollmentStatus;


import java.time.Instant;
public record EnrollmentResponse(Long id, Long courseId, String courseTitle, Instant enrolledAt,
                                 EnrollmentStatus status, int progressPercentage) {}
