package com.sep490.backend.modules.learning.service;

import lombok.Builder;
import lombok.Getter;

public final class UserLevelCalculator {

    private UserLevelCalculator() {}

    public record LevelDefinition(
            int level,
            int minXp,
            int maxXp,
            String titleVi,
            String titleEn
    ) {}

    private static final LevelDefinition[] LEVELS = {
            new LevelDefinition(1, 0, 99, "Tập Sự Khởi Đầu", "Novice Beginner"),
            new LevelDefinition(2, 100, 249, "Người Khám Phá", "Curious Explorer"),
            new LevelDefinition(3, 250, 499, "Học Viên Chăm Chỉ", "Diligent Learner"),
            new LevelDefinition(4, 500, 849, "Người Kết Nối", "Active Connector"),
            new LevelDefinition(5, 850, 1299, "Giao Tiếp Tự Tin", "Confident Speaker"),
            new LevelDefinition(6, 1300, 1849, "Nhà Đàm Thoại", "Conversationalist"),
            new LevelDefinition(7, 1850, 2499, "Hiểu Biết Sâu Rộng", "Polymath"),
            new LevelDefinition(8, 2500, 3299, "Sành Tiếng Việt", "Fluent Voyager"),
            new LevelDefinition(9, 3300, 4249, "Chuyên Gia Ngôn Ngữ", "Language Expert"),
            new LevelDefinition(10, 4250, Integer.MAX_VALUE, "Bậc Thầy Tiếng Việt", "Master of Vietnamese")
    };

    @Getter
    @Builder
    public static class LevelInfo {
        private final int currentLevel;
        private final String levelTitle;
        private final String levelTitleEn;
        private final int currentLevelMinXp;
        private final int nextLevelRequiredXp;
        private final int levelProgressPercent;
    }

    public static LevelInfo calculate(int totalXp) {
        int safeXp = Math.max(0, totalXp);
        LevelDefinition currentDef = LEVELS[0];
        LevelDefinition nextDef = LEVELS.length > 1 ? LEVELS[1] : null;

        for (int i = 0; i < LEVELS.length; i++) {
            if (safeXp >= LEVELS[i].minXp() && (safeXp <= LEVELS[i].maxXp() || i == LEVELS.length - 1)) {
                currentDef = LEVELS[i];
                nextDef = (i < LEVELS.length - 1) ? LEVELS[i + 1] : null;
                break;
            }
        }

        int minXp = currentDef.minXp();
        int nextXp;
        int progressPercent;

        if (nextDef != null) {
            nextXp = nextDef.minXp();
            int span = nextXp - minXp;
            int xpInLevel = safeXp - minXp;
            progressPercent = span > 0 ? Math.min(100, (xpInLevel * 100) / span) : 100;
        } else {
            // Max level reached
            nextXp = currentDef.minXp();
            progressPercent = 100;
        }

        return LevelInfo.builder()
                .currentLevel(currentDef.level())
                .levelTitle(currentDef.titleVi())
                .levelTitleEn(currentDef.titleEn())
                .currentLevelMinXp(minXp)
                .nextLevelRequiredXp(nextXp)
                .levelProgressPercent(progressPercent)
                .build();
    }
}
