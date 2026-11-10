package com.sep490.backend.modules.learning.progress.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserLevelCalculatorTests {

    @Test
    @DisplayName("Level 1 for 0 - 99 XP")
    void testLevelOne() {
        var infoZero = UserLevelCalculator.calculate(0);
        assertEquals(1, infoZero.getCurrentLevel());
        assertEquals("Tập Sự Khởi Đầu", infoZero.getLevelTitle());
        assertEquals(0, infoZero.getCurrentLevelMinXp());
        assertEquals(100, infoZero.getNextLevelRequiredXp());
        assertEquals(0, infoZero.getLevelProgressPercent());

        var infoHalf = UserLevelCalculator.calculate(50);
        assertEquals(1, infoHalf.getCurrentLevel());
        assertEquals(50, infoHalf.getLevelProgressPercent());
    }

    @Test
    @DisplayName("Level 2 for 100 - 249 XP")
    void testLevelTwo() {
        var info = UserLevelCalculator.calculate(100);
        assertEquals(2, info.getCurrentLevel());
        assertEquals("Người Khám Phá", info.getLevelTitle());
        assertEquals(100, info.getCurrentLevelMinXp());
        assertEquals(250, info.getNextLevelRequiredXp());
        assertEquals(0, info.getLevelProgressPercent());

        var infoMid = UserLevelCalculator.calculate(175);
        assertEquals(2, infoMid.getCurrentLevel());
        assertEquals(50, infoMid.getLevelProgressPercent());
    }

    @Test
    @DisplayName("Level 10 for 4250+ XP")
    void testLevelTenMaster() {
        var info = UserLevelCalculator.calculate(5000);
        assertEquals(10, info.getCurrentLevel());
        assertEquals("Bậc Thầy Tiếng Việt", info.getLevelTitle());
        assertEquals(4250, info.getCurrentLevelMinXp());
        assertEquals(100, info.getLevelProgressPercent());
    }
}
