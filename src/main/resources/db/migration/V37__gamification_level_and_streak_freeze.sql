-- Gamification: Streak Freeze & Multi-Tier Badges

ALTER TABLE users ADD COLUMN streak_freeze_count INT DEFAULT 1;
ALTER TABLE users ADD COLUMN streak_freeze_used_date DATE NULL;

-- Update existing users with default 1 streak freeze if null
UPDATE users SET streak_freeze_count = 1 WHERE streak_freeze_count IS NULL;

-- Insert or update Multi-Tier Badges
INSERT INTO badges (code, name, description, icon_url, category, condition_type, condition_value, xp_reward, status)
VALUES
('FIRST_LESSON', 'Bước Đầu Tiên', 'Hoàn thành bài học đầu tiên của bạn', '/badges/first_lesson.png', 'LESSON', 'LESSON_COMPLETED', 1, 50, 'ACTIVE'),
('LESSON_5', 'Người Chăm Học', 'Hoàn thành 5 bài học tiếng Việt', '/badges/lesson_5.png', 'LESSON', 'LESSON_COMPLETED', 5, 100, 'ACTIVE'),
('LESSON_20', 'Hành Trình Bền Bỉ', 'Hoàn thành 20 bài học tiếng Việt', '/badges/lesson_20.png', 'LESSON', 'LESSON_COMPLETED', 20, 250, 'ACTIVE'),

('STREAK_3_DAYS', 'Khởi Động Giữ Lửa', 'Duy trì chuỗi học 3 ngày liên tục', '/badges/streak_3.png', 'STREAK', 'STREAK_DAYS', 3, 30, 'ACTIVE'),
('STREAK_7_DAYS', 'Thói Quên Vàng', 'Duy trì chuỗi 7 ngày học liên tục', '/badges/streak_7.png', 'STREAK', 'STREAK_DAYS', 7, 100, 'ACTIVE'),
('STREAK_14_DAYS', 'Kiên Trì Bền Bỉ', 'Duy trì chuỗi 14 ngày học liên tục', '/badges/streak_14.png', 'STREAK', 'STREAK_DAYS', 14, 150, 'ACTIVE'),
('STREAK_30_DAYS', 'Kẻ Giữ Lửa Bất Diệt', 'Duy trì chuỗi 30 ngày học liên tục', '/badges/streak_30.png', 'STREAK', 'STREAK_DAYS', 30, 300, 'ACTIVE'),

('VOCAB_10', 'Vườn Từ Đầu Tiên', 'Lưu và học 10 từ vựng vào sổ tay', '/badges/vocab_10.png', 'VOCABULARY', 'VOCAB_LEARNED', 10, 50, 'ACTIVE'),
('VOCAB_50', 'Nhà Sưu Tầm Từ Vựng', 'Học và lưu 50 từ vựng vào sổ tay', '/badges/vocab_50.png', 'VOCABULARY', 'VOCAB_LEARNED', 50, 150, 'ACTIVE'),
('VOCAB_100', 'Từ Điển Sống', 'Học và lưu 100 từ vựng vào sổ tay', '/badges/vocab_100.png', 'VOCABULARY', 'VOCAB_LEARNED', 100, 300, 'ACTIVE'),

('QUIZ_PERFECT_1', 'Mắt Đại Bàng', 'Đạt điểm 100% tuyệt đối trong 1 bài kiểm tra', '/badges/quiz_perfect_1.png', 'QUIZ', 'QUIZ_PERFECT', 1, 50, 'ACTIVE'),
('QUIZ_MASTER', 'Bậc Thầy Quiz', 'Hoàn thành 10 bài kiểm tra đạt điểm tối đa', '/badges/quiz_master.png', 'QUIZ', 'QUIZ_PERFECT', 10, 200, 'ACTIVE'),

('AI_ROLEPLAY_1', 'Lời Chào Đầu Tiên', 'Hoàn thành phiên luyện nói hội thoại AI đầu tiên', '/badges/ai_roleplay_1.png', 'AI_TUTOR', 'AI_ROLEPLAY_COMPLETED', 1, 50, 'ACTIVE'),
('AI_ROLEPLAY_5', 'Tự Tin Giao Tiếp', 'Hoàn thành 5 tình huống hội thoại giao tiếp cùng AI', '/badges/ai_roleplay_5.png', 'AI_TUTOR', 'AI_ROLEPLAY_COMPLETED', 5, 150, 'ACTIVE')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    icon_url = VALUES(icon_url),
    category = VALUES(category),
    condition_type = VALUES(condition_type),
    condition_value = VALUES(condition_value),
    xp_reward = VALUES(xp_reward),
    status = VALUES(status);
