package com.sep490.backend.modules.admin.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
public class AdminDashboardStatsResponse {
    private long totalUsers;
    private long activeLearners;
    private long totalCourses;
    private long totalLessons;
    private long totalXpAwarded;
    private long totalXpGranted;
    private List<Map<String, Object>> userGrowth;
    private List<Map<String, Object>> recentActivities;
}
