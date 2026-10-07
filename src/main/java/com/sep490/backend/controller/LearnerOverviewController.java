package com.sep490.backend.controller;

import com.sep490.backend.config.LearnerPrincipal;
import com.sep490.backend.dto.response.ApiResponse;
import com.sep490.backend.learning.shared.ContentStore;
import com.sep490.backend.service.MyCoursesService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Adapter for Huy's learner dashboard, backed by Member 2 learning records. */
@RestController
@RequiredArgsConstructor
public class LearnerOverviewController {
    private final MyCoursesService courses;
    private final ContentStore db;
    private final com.sep490.backend.service.UserProgressService userProgress;

    @GetMapping("/api/users/progress")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> overview(@AuthenticationPrincipal LearnerPrincipal user) {
        var enrolled = courses.myCourses(user.id());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enrolledCourses", enrolled.stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.courseId()); row.put("title", c.title());
            row.put("image", c.thumbnailUrl()); row.put("progress", c.progressPercentage());
            row.put("nextLesson", c.progressPercentage() == 100 ? "Đã hoàn thành" : "Chọn bài học trong khóa");
            return row;
        }).toList());
        result.put("completedLessons", enrolled.stream().mapToInt(c -> c.completedLessons()).sum());
        result.put("savedVocabCount", db.count("SELECT COUNT(*) FROM vocabulary_entries WHERE user_id=?", user.id()));
        result.put("recentVocab", db.rows("SELECT word AS vi,meaning AS en FROM vocabulary_entries WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 6", user.id()));
        result.put("quizHistory", db.rows("SELECT id,quiz_title AS quiz_name,percentage AS score,submitted_at AS date,passed FROM quiz_attempts WHERE user_id=? AND submitted_at IS NOT NULL ORDER BY submitted_at DESC,id DESC LIMIT 50", user.id()));
        result.put("currentLesson", null);
        // These metrics belong to other integrations and have not been measured yet.
        for (String key : List.of("totalTimeMinutes", "masteredWords", "currentStreak", "longestStreak", "totalXp", "todayMinutes", "journeyProgress")) result.put(key, null);
        result.put("level", "Chưa có đánh giá"); result.put("nextLevelXp", 600);
        result.put("dailyGoalMinutes", 15); result.put("journeyCity", "Chưa có dữ liệu");
        result.put("weeklyActivity", List.of()); result.put("recentActivities", List.of());
        var summary = userProgress.getUserProgress();
        result.put("currentStreak", summary.getStreakCount());
        result.put("streakCount", summary.getStreakCount());
        result.put("totalXp", summary.getTotalXp());
        result.put("dailyGoalMinutes", summary.getDailyGoalMinutes());
        result.put("lastActivityDate", summary.getLastActivityDate());
        result.put("recentXpTransactions", summary.getRecentXpTransactions());
        result.put("completedLessonsCount", result.get("completedLessons"));
        result.put("learnedVocabulariesCount", result.get("savedVocabCount"));
        return ApiResponse.<Map<String, Object>>builder().result(result).build();
    }
}
