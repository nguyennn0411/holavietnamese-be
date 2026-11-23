-- Flyway Migration V13: AI Tutor and Roleplay
-- Maps to UC-099 .. UC-108 in Report 3 (AI Tutor & Roleplay)

CREATE TABLE IF NOT EXISTS ai_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    active_model VARCHAR(100) NOT NULL DEFAULT 'gemini-1.5-flash',
    system_prompt_version VARCHAR(50) NOT NULL DEFAULT 'v2.2.0-stable',
    system_prompt TEXT,
    max_turns_per_session INT NOT NULL DEFAULT 25,
    max_tokens_per_response INT NOT NULL DEFAULT 1024,
    rate_limit_per_minute INT NOT NULL DEFAULT 30,
    daily_sessions_per_user INT NOT NULL DEFAULT 40,
    monthly_budget_cap_usd DECIMAL(10, 2) NOT NULL DEFAULT 150.00,
    current_month_spend_usd DECIMAL(10, 2) NOT NULL DEFAULT 42.68,
    cost_alert_threshold_percent INT NOT NULL DEFAULT 80,
    api_key_configured BOOLEAN NOT NULL DEFAULT FALSE,
    enabled_modes VARCHAR(500) NOT NULL DEFAULT 'tutor,grammar,translation,conversation,culture_guide,travel,regional',
    context_options VARCHAR(500) NOT NULL DEFAULT 'learner_level,course,destination,vocabulary',
    moderation_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE TABLE IF NOT EXISTS ai_scenarios (
    id VARCHAR(100) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    level VARCHAR(20) NOT NULL DEFAULT 'A1',
    topic VARCHAR(50) NOT NULL DEFAULT 'dining',
    destination VARCHAR(100) NOT NULL DEFAULT 'Hà Nội',
    difficulty VARCHAR(50) NOT NULL DEFAULT 'Dễ',
    duration VARCHAR(50) DEFAULT '5-10 phút',
    thumbnail VARCHAR(1000),
    cover_image VARCHAR(1000),
    overview TEXT,
    ai_role_name VARCHAR(100) NOT NULL,
    ai_role_title VARCHAR(255),
    ai_role_avatar VARCHAR(50),
    ai_role_tone TEXT,
    ai_role_initial_greeting TEXT NOT NULL,
    learner_role_title VARCHAR(255),
    learner_role_context TEXT,
    objectives_json JSON,
    vocabulary_hints_json JSON,
    cultural_tip TEXT,
    sample_hints_json JSON,
    max_turns INT DEFAULT 10,
    criteria_json JSON,
    completions_count INT NOT NULL DEFAULT 0,
    avg_score INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE TABLE IF NOT EXISTS ai_sessions (
    id VARCHAR(100) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    mode VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    scenario_id VARCHAR(100),
    learner_level VARCHAR(20),
    destination VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    total_score INT,
    rating VARCHAR(50),
    stats_json JSON,
    completed_tasks_json JSON,
    ai_feedback TEXT,
    corrections_json JSON,
    practiced_expressions_json JSON,
    started_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ended_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ai_session_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_ai_session_scenario FOREIGN KEY (scenario_id) REFERENCES ai_scenarios(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS ai_messages (
    id VARCHAR(100) PRIMARY KEY,
    session_id VARCHAR(100) NOT NULL,
    sender VARCHAR(20) NOT NULL,
    mode VARCHAR(50) NOT NULL,
    text MEDIUMTEXT NOT NULL,
    translation TEXT,
    explanation TEXT,
    grammar_check_json JSON,
    saved_words_json JSON,
    tokens_in INT DEFAULT 0,
    tokens_out INT DEFAULT 0,
    latency_ms INT DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ai_message_session FOREIGN KEY (session_id) REFERENCES ai_sessions(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ai_reviews (
    id VARCHAR(100) PRIMARY KEY,
    session_id VARCHAR(100) NOT NULL,
    user_id BIGINT NOT NULL,
    scenario_id VARCHAR(100),
    scenario_title VARCHAR(255),
    rating INT NOT NULL,
    user_feedback TEXT,
    tagged_issues_json JSON,
    admin_note TEXT,
    reviewed_by_admin BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(50) NOT NULL DEFAULT 'APPROVED',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ai_review_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_ai_review_session FOREIGN KEY (session_id) REFERENCES ai_sessions(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ai_usage_logs (
    id VARCHAR(100) PRIMARY KEY,
    user_id BIGINT,
    session_id VARCHAR(100),
    mode VARCHAR(50) NOT NULL,
    tokens_in INT NOT NULL DEFAULT 0,
    tokens_out INT NOT NULL DEFAULT 0,
    latency_ms INT NOT NULL DEFAULT 0,
    cost_usd DECIMAL(10, 5) NOT NULL DEFAULT 0.00000,
    status_code INT NOT NULL DEFAULT 200,
    error_message TEXT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ai_usage_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Seed initial default AI Settings if not exists
INSERT INTO ai_settings (id, active_model, system_prompt_version, max_turns_per_session, max_tokens_per_response, rate_limit_per_minute, daily_sessions_per_user, monthly_budget_cap_usd, current_month_spend_usd, cost_alert_threshold_percent, api_key_configured, enabled_modes)
VALUES (1, 'gemini-1.5-flash', 'v2.2.0-stable', 25, 1024, 30, 40, 150.00, 42.68, 80, TRUE, 'tutor,grammar,translation,conversation,culture_guide,travel,regional')
ON DUPLICATE KEY UPDATE active_model = VALUES(active_model);

-- Seed Initial approved Roleplay Scenarios from Report 3
INSERT INTO ai_scenarios (
    id, title, level, topic, destination, difficulty, duration, thumbnail, cover_image, overview,
    ai_role_name, ai_role_title, ai_role_avatar, ai_role_tone, ai_role_initial_greeting,
    learner_role_title, learner_role_context,
    objectives_json, vocabulary_hints_json, cultural_tip, sample_hints_json, max_turns, status
) VALUES (
    'goi-mon-pho-ha-noi',
    'Gọi món tại quán Phở gia truyền Hà Nội',
    'A1',
    'dining',
    'Hà Nội',
    'Dễ',
    '5-10 phút',
    'https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?auto=format&fit=crop&w=800&q=80',
    'https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?auto=format&fit=crop&w=1200&q=80',
    'Bạn vừa đặt chân đến phố cổ Hà Nội vào một buổi sáng sớm se lạnh và ghé vào một quán phở bò gia truyền đông đúc trên phố Bát Đàn.',
    'Cô Mai (Chủ quán Phở)',
    'Chủ quán phở Hà Nội nhanh nhẹn, xưng hô cô - cháu',
    '👩‍🍳',
    'Thân thiện, xởi lởi, giọng Bắc chuẩn Tràng An, phục vụ nhanh nhẹn',
    'Chào cháu! Quán cô đang có bàn trống trong góc kìa, vào ngồi đi cháu! Hôm nay cháu muốn ăn phở bò tái, chín, hay nạm gầu nào?',
    'Du khách lần đầu thưởng thức phở truyền thống',
    'Bạn cần tìm chỗ ngồi, gọi một bát phở bò theo khẩu vị riêng (ít hành, không cay) và gọi thêm đồ uống.',
    '[{"id":"task-1","label":"Chào hỏi và xác nhận chỗ ngồi tại quán"},{"id":"task-2","label":"Gọi một bát phở bò (tái/chín) kèm yêu cầu đặc biệt (ít hành/ớt)"},{"id":"task-3","label":"Gọi thêm đồ uống (trà đá/sữa đậu nành) và quẩy giòn"},{"id":"task-4","label":"Hỏi giá tiền và cảm ơn thanh toán"}]',
    '[{"word":"Bát phở tái chín","meaning":"Bowl of pho with rare and well-done beef"},{"word":"Cho cháu ít hành thôi ạ","meaning":"Please give me just a little scallion"},{"word":"Một đĩa quẩy giòn","meaning":"A plate of crispy fried dough sticks"},{"word":"Một ly trà đá","meaning":"An iced green tea"},{"word":"Hết bao nhiêu tiền ạ?","meaning":"How much does it cost in total?"}]',
    'Tại các quán phở truyền thống miền Bắc, quẩy được nhúng trực tiếp vào nước dùng phở để hút trọn vị ngọt béo của xương bò.',
    '["Dạ chào cô! Cho cháu một bát phở bò tái chín, ít hành ít ớt nhé cô!","Cô cho cháu thêm một đĩa quẩy giòn và một cốc trà đá với ạ!","Cô ơi của cháu hết tất cả bao nhiêu tiền ạ?"]',
    10,
    'ACTIVE'
),
(
    'tra-gia-cho-ben-thanh',
    'Trả giá quà lưu niệm tại Chợ Bến Thành',
    'A2',
    'shopping',
    'TP. Hồ Chí Minh',
    'Trung bình',
    '8-12 phút',
    'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=800&q=80',
    'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=1200&q=80',
    'Bạn ghé thăm chợ Bến Thành ở Sài Gòn để mua quà lưu niệm cho bạn bè: nón lá vẽ tay và cà phê Robusta rang xay thơm lừng.',
    'Chị Ba Bến Thành',
    'Tiểu thương sành sỏi, hoạt ngôn, xưng hô chị - em',
    '🧣',
    'Niềm nở, dẻo miệng, giọng Nam Bộ ngọt ngào nhưng báo giá hơi cao',
    'Ủa em trai / em gái, ghé sạp chị Ba coi nón lá với cà phê nè! Hàng xịn thủ công loại một đó cưng, mở hàng cho chị một món lấy hên đi!',
    'Khách du lịch muốn mua quà lưu niệm với giá hợp lý',
    'Hỏi giá, khen sản phẩm đẹp, nhưng khéo léo thương lượng giảm từ 20-30% hoặc mua combo để được giá tốt.',
    '[{"id":"task-1","label":"Hỏi giá món đồ lưu niệm bằng tiếng Việt"},{"id":"task-2","label":"Khen món đồ và đưa ra đề xuất giảm giá lịch sự"},{"id":"task-3","label":"Đàm phán mua số lượng 2 món để được bớt thêm"},{"id":"task-4","label":"Chốt giá thân thiện và hoàn tất thanh toán"}]',
    '[{"word":"Cái nón này bao nhiêu tiền chị?","meaning":"How much is this conical hat, sister?"},{"word":"Đắt quá, bớt cho em chút đi!","meaning":"Too expensive, give me a discount please!"},{"word":"Nếu em lấy 2 cái thì chị tính bao nhiêu?","meaning":"If I take 2, how much will you charge?"},{"word":"Giá hữu nghị / Giá mở hàng","meaning":"Friendly price / First-sale good luck price"}]',
    'Ở miền Nam, nụ cười tươi và cách xưng hô thân mật Chị Ba ơi, bớt em xíu nghen luôn là vũ khí trả giá hiệu quả nhất!',
    '["Cái nón này bao nhiêu tiền chị Ba?","Bớt cho em chút lấy may mắn đi chị, hai trăm nhé?","Nếu em lấy 2 cái thì chị tính bao nhiêu nè?"]',
    12,
    'ACTIVE'
),
(
    'bat-xe-om-cong-nghe',
    'Đón xe ôm công nghệ & Chỉ đường đến điểm hẹn',
    'A2',
    'travel',
    'Hà Nội / Sài Gòn',
    'Dễ - Trung bình',
    '6-10 phút',
    'https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?auto=format&fit=crop&w=800&q=80',
    'https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?auto=format&fit=crop&w=1200&q=80',
    'Bạn vừa đặt một chuyến xe ôm công nghệ trên app và bác tài xế gọi điện thoại xác nhận điểm đón.',
    'Bác Hùng (Tài xế công nghệ)',
    'Tài xế xe ôm nhiều năm kinh nghiệm, nhiệt tình và mến khách',
    '🛵',
    'Chân chất, hào sảng, xưng chú - cháu/con',
    'A lô, cháu có phải là người đặt chuyến xe đi Bảo tàng Lịch sử không? Chú đang chạy tới gần rồi, cháu mặc áo màu gì, đứng chỗ nào để chú tấp vào đón?',
    'Hành khách đang chờ đón ở cổng địa điểm',
    'Mô tả vị trí đang đứng, nhận diện biển số xe, yêu cầu tài xế chạy cẩn thận và hỏi han giao thông trên đường.',
    '[{"id":"task-1","label":"Mô tả vị trí và trang phục nhận diện cho tài xế"},{"id":"task-2","label":"Xác nhận biển số xe và đội mũ bảo hiểm"},{"id":"task-3","label":"Trò chuyện ngắn về đường xá hoặc thời tiết hôm nay"},{"id":"task-4","label":"Hỏi quét mã QR thanh toán và gửi lời cảm ơn"}]',
    '[{"word":"Cháu đang đứng trước cổng","meaning":"I am standing in front of the gate"},{"word":"Biển số xe của chú là 29-B1...","meaning":"Your license plate is 29-B1..."},{"word":"Đoạn này có hay bị kẹt xe không chú?","meaning":"Does this stretch often get traffic jams, uncle?"},{"word":"Cháu quét mã QR chuyển khoản nhé","meaning":"I will scan the QR code to transfer money"}]',
    'Các bác tài xế xe ôm Việt Nam rất thích trò chuyện vui vẻ về cuộc sống và sẵn sàng chỉ cho bạn các quán ăn địa phương ngon nhất.',
    '["Cháu đang đứng trước cổng khách sạn ạ","Chú cho cháu quét mã QR chuyển khoản nhé"]',
    8,
    'ACTIVE'
)
ON DUPLICATE KEY UPDATE title = VALUES(title);

