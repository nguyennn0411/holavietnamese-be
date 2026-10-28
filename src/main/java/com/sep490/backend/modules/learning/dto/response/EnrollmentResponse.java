package com.sep490.backend.modules.learning.dto.response;

import java.time.Instant;
import com.sep490.backend.modules.learning.entity.enums.EnrollmentStatus;
public record EnrollmentResponse(Long id, Long courseId, String courseTitle, Instant enrolledAt,
                                 EnrollmentStatus status, int progressPercentage) {}
