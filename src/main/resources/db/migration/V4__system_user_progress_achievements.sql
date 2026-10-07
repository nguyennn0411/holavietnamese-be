-- Additional columns for user authentication, onboarding, settings, and streak
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verification_token VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_token_expiry TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_password_otp VARCHAR(10);
ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_password_otp_expiry TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS country VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS daily_learning_goal_minutes INT DEFAULT 15;
ALTER TABLE users ADD COLUMN IF NOT EXISTS audio_speed DOUBLE DEFAULT 1.0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS pronunciation_hints_enabled BOOLEAN DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS auto_translate_enabled BOOLEAN DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS notifications_enabled BOOLEAN DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS onboarding_completed BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS streak_count INT DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_activity_date DATE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS total_xp INT DEFAULT 0;

-- Badges table
CREATE TABLE IF NOT EXISTS badges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    icon_url VARCHAR(500),
    category VARCHAR(50) DEFAULT 'GENERAL',
    condition_type VARCHAR(50) NOT NULL,
    condition_value INT NOT NULL DEFAULT 1,
    xp_reward INT NOT NULL DEFAULT 50,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- User Badges table
CREATE TABLE IF NOT EXISTS user_badges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    badge_id BIGINT NOT NULL,
    unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    badge_level INT DEFAULT 1,
    CONSTRAINT uk_user_badge UNIQUE (user_id, badge_id),
    CONSTRAINT fk_user_badge_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_badge_badge FOREIGN KEY (badge_id) REFERENCES badges(id) ON DELETE CASCADE
);

-- XP Transactions table
CREATE TABLE IF NOT EXISTS xp_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    amount INT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_xp_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Notifications table
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50) DEFAULT 'SYSTEM',
    link_url VARCHAR(500),
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- System Settings table
CREATE TABLE IF NOT EXISTS system_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    setting_value TEXT NOT NULL,
    description VARCHAR(255),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- XP Rules table
CREATE TABLE IF NOT EXISTS xp_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL UNIQUE,
    event_name VARCHAR(100) NOT NULL,
    xp_reward INT NOT NULL DEFAULT 10,
    daily_cap INT DEFAULT 100,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Audit Logs table
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_username VARCHAR(100) NOT NULL,
    action VARCHAR(100) NOT NULL,
    target VARCHAR(200),
    details TEXT,
    ip_address VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Initial Badges Seeder
INSERT IGNORE INTO badges (code, name, description, icon_url, category, condition_type, condition_value, xp_reward) VALUES
('FIRST_LESSON', 'Bước Đầu Tiên', 'Hoàn thành bài học đầu tiên của bạn', '/badges/first_lesson.png', 'LESSON', 'LESSON_COMPLETED', 1, 50),
('STREAK_7_DAYS', 'Thói Quên Vàng', 'Duy trì chuỗi 7 ngày học liên tục', '/badges/streak_7.png', 'STREAK', 'STREAK_DAYS', 7, 100),
('VOCAB_50', 'Nhà Sưu Tầm Từ Vựng', 'Học và lưu 50 từ vựng vào sổ tay', '/badges/vocab_50.png', 'VOCABULARY', 'VOCAB_LEARNED', 50, 150),
('QUIZ_MASTER', 'Bậc Thầy Quiz', 'Hoàn thành 10 bài kiểm tra đạt điểm tối đa', '/badges/quiz_master.png', 'QUIZ', 'QUIZ_PERFECT', 10, 200);

-- Initial XP Rules Seeder
INSERT IGNORE INTO xp_rules (event_type, event_name, xp_reward, daily_cap) VALUES
('LESSON_COMPLETE', 'Hoàn thành 1 bài học', 20, 200),
('DAILY_LOGIN', 'Đăng nhập mỗi ngày', 10, 10),
('QUIZ_PASS', 'Đạt bài kiểm tra', 15, 150),
('VOCAB_LEARNED', 'Học 1 từ mới', 5, 50);

-- Initial System Settings Seeder
INSERT IGNORE INTO system_settings (setting_key, setting_value, description) VALUES
('APP_NAME', 'Hola Vietnamese', 'Tên ứng dụng hệ thống'),
('DEFAULT_DAILY_GOAL_MINUTES', '15', 'Mục tiêu học hàng ngày mặc định (phút)'),
('ENABLE_USER_REGISTRATION', 'true', 'Cho phép người dùng mới đăng ký');
