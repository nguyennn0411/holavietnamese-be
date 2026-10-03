package com.sep490.backend.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AdminDashboardStatsResponse {
    private long totalUsers;
    private long activeLearners;
    private long totalCourses;
    private long totalLessons;
    private long totalXpAwarded;
}
