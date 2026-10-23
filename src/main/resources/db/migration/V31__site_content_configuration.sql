-- Database-backed navigation, homepage banners, and UI feature switches.

CREATE TABLE site_feature_flags (
    feature_key VARCHAR(100) PRIMARY KEY,
    name_vi VARCHAR(200) NOT NULL,
    name_en VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE TABLE site_menu_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(100) NOT NULL,
    placement VARCHAR(40) NOT NULL,
    label_vi VARCHAR(200) NOT NULL,
    label_en VARCHAR(200) NOT NULL,
    path VARCHAR(500),
    icon_key VARCHAR(100),
    display_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    auth_required BOOLEAN NOT NULL DEFAULT FALSE,
    mobile_primary BOOLEAN NOT NULL DEFAULT FALSE,
    feature_key VARCHAR(100),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_site_menu_items_code UNIQUE (code),
    CONSTRAINT fk_site_menu_items_feature FOREIGN KEY (feature_key)
        REFERENCES site_feature_flags(feature_key) ON DELETE SET NULL
);

CREATE INDEX idx_site_menu_items_public
    ON site_menu_items(placement, enabled, display_order);

CREATE TABLE site_banners (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(100) NOT NULL,
    placement VARCHAR(40) NOT NULL,
    eyebrow_vi VARCHAR(255),
    eyebrow_en VARCHAR(255),
    title_vi VARCHAR(255) NOT NULL,
    title_en VARCHAR(255) NOT NULL,
    description_vi VARCHAR(2000),
    description_en VARCHAR(2000),
    image_url VARCHAR(2048) NOT NULL,
    image_alt_vi VARCHAR(500),
    image_alt_en VARCHAR(500),
    photo_label_vi VARCHAR(255),
    photo_label_en VARCHAR(255),
    action_type VARCHAR(40) NOT NULL DEFAULT 'NONE',
    action_label_vi VARCHAR(255),
    action_label_en VARCHAR(255),
    action_target VARCHAR(2048),
    display_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    starts_at TIMESTAMP(6),
    ends_at TIMESTAMP(6),
    feature_key VARCHAR(100),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_site_banners_code UNIQUE (code),
    CONSTRAINT fk_site_banners_feature FOREIGN KEY (feature_key)
        REFERENCES site_feature_flags(feature_key) ON DELETE SET NULL
);

CREATE INDEX idx_site_banners_public
    ON site_banners(placement, enabled, display_order);
CREATE INDEX idx_site_banners_schedule
    ON site_banners(starts_at, ends_at);

INSERT INTO site_feature_flags (feature_key, name_vi, name_en, description, enabled) VALUES
('HOME', 'Trang chủ', 'Home', 'Hiển thị mục trang chủ.', TRUE),
('LEARNING_PATH', 'Lộ trình học', 'Learning path', 'Hiển thị lộ trình học tập.', TRUE),
('COURSES', 'Khóa học', 'Courses', 'Hiển thị danh mục và nội dung khóa học.', TRUE),
('ALPHABET', 'Bảng chữ cái và phát âm', 'Alphabet and pronunciation', 'Hiển thị nội dung bảng chữ cái và luyện phát âm.', TRUE),
('VOCABULARY', 'Từ vựng', 'Vocabulary', 'Hiển thị danh mục, sổ tay và ôn tập từ vựng.', TRUE),
('EXPLORE', 'Khám phá Việt Nam', 'Explore Vietnam', 'Hiển thị nội dung điểm đến Việt Nam.', TRUE),
('CULTURE', 'Văn hóa Việt Nam', 'Vietnamese culture', 'Hiển thị nội dung văn hóa.', TRUE),
('BLOG', 'Blog', 'Blog', 'Hiển thị blog.', TRUE),
('MY_LEARNING', 'Góc học tập', 'My learning', 'Hiển thị góc học tập cá nhân.', TRUE),
('LISTENING', 'Luyện nghe', 'Listening', 'Hiển thị mục luyện nghe.', TRUE),
('AI_SPEAKING', 'Luyện nói', 'Speaking practice', 'Hiển thị tình huống luyện nói.', TRUE),
('AI_TUTOR', 'Gia sư AI', 'AI tutor', 'Hiển thị các trải nghiệm hội thoại AI.', TRUE),
('GUEST_HOME_HERO', 'Banner trang chủ khách', 'Guest home hero', 'Hiển thị carousel trang chủ dành cho khách.', TRUE),
('LEARNER_HOME_HERO', 'Banner trang chủ học viên', 'Learner home hero', 'Hiển thị carousel trang chủ dành cho học viên.', TRUE);

INSERT INTO site_menu_items
    (code, placement, label_vi, label_en, path, icon_key, display_order, enabled, auth_required, mobile_primary, feature_key)
VALUES
('GUEST_HOME', 'GUEST_SIDEBAR', 'Trang chủ', 'Home', '/', 'home', 10, TRUE, FALSE, FALSE, 'HOME'),
('GUEST_LEARNING_PATH', 'GUEST_SIDEBAR', 'Lộ trình học', 'Learning Path', '/edtech/dashboard', 'compass', 20, TRUE, TRUE, FALSE, 'LEARNING_PATH'),
('GUEST_COURSES', 'GUEST_SIDEBAR', 'Khóa học', 'Courses', '/courses', 'graduation-cap', 30, TRUE, FALSE, FALSE, 'COURSES'),
('GUEST_ALPHABET', 'GUEST_SIDEBAR', 'Bảng chữ cái tiếng Việt', 'Vietnamese Alphabet', '/alphabet', 'languages', 40, TRUE, FALSE, FALSE, 'ALPHABET'),
('GUEST_VOCABULARY', 'GUEST_SIDEBAR', 'Từ vựng', 'Vocabulary', '/vocabulary', 'book-open', 50, TRUE, TRUE, FALSE, 'VOCABULARY'),
('GUEST_EXPLORE', 'GUEST_SIDEBAR', 'Khám phá Việt Nam', 'Explore Vietnam', '/explore-vietnam', 'compass', 60, TRUE, TRUE, FALSE, 'EXPLORE'),
('GUEST_CULTURE', 'GUEST_SIDEBAR', 'Văn hóa Việt Nam', 'Vietnamese Culture', '/culture', 'palette', 70, TRUE, TRUE, FALSE, 'CULTURE'),
('GUEST_BLOG', 'GUEST_SIDEBAR', 'Blog', 'Blog', '/blog', 'newspaper', 80, TRUE, FALSE, FALSE, 'BLOG'),
('GUEST_AI_TUTOR', 'GUEST_SIDEBAR', 'Gia sư AI', 'AI Tutor', '/ai-tutor', 'bot', 90, TRUE, TRUE, FALSE, 'AI_TUTOR'),

('LEARNER_HOME', 'LEARNER_SIDEBAR', 'Học tập', 'Learn', '/', 'home', 10, TRUE, FALSE, TRUE, 'HOME'),
('LEARNER_LEARNING_PATH', 'LEARNER_SIDEBAR', 'Lộ trình học', 'Learning Path', '/edtech/dashboard', 'compass', 20, TRUE, TRUE, TRUE, 'LEARNING_PATH'),
('LEARNER_COURSES', 'LEARNER_SIDEBAR', 'Khóa học', 'Courses', '/courses', 'book-open', 30, TRUE, FALSE, TRUE, 'COURSES'),
('LEARNER_MY_LEARNING', 'LEARNER_SIDEBAR', 'Góc học tập', 'My learning', '/my-learning', 'graduation-cap', 40, TRUE, TRUE, FALSE, 'MY_LEARNING'),
('LEARNER_VOCABULARY', 'LEARNER_SIDEBAR', 'Từ vựng', 'Vocabulary', '/vocabulary', 'languages', 50, TRUE, TRUE, TRUE, 'VOCABULARY'),
('LEARNER_EXPLORE', 'LEARNER_SIDEBAR', 'Khám phá Việt Nam', 'Explore Vietnam', '/explore', 'compass', 60, TRUE, TRUE, FALSE, 'EXPLORE'),
('LEARNER_CULTURE', 'LEARNER_SIDEBAR', 'Văn hóa Việt Nam', 'Vietnamese Culture', '/culture', 'palette', 70, TRUE, TRUE, FALSE, 'CULTURE'),
('LEARNER_BLOG', 'LEARNER_SIDEBAR', 'Blog', 'Blog', '/blog', 'newspaper', 80, TRUE, FALSE, FALSE, 'BLOG'),
('LEARNER_PRONUNCIATION', 'LEARNER_SIDEBAR', 'Phát âm', 'Pronunciation', '/alphabet', 'mic-2', 90, TRUE, FALSE, TRUE, 'ALPHABET'),
('LEARNER_LISTENING', 'LEARNER_SIDEBAR', 'Luyện nghe', 'Listening', NULL, 'headphones', 100, FALSE, TRUE, FALSE, 'LISTENING'),
('LEARNER_SPEAKING', 'LEARNER_SIDEBAR', 'Luyện nói', 'Speaking Practice', '/ai-scenarios', 'graduation-cap', 110, TRUE, TRUE, FALSE, 'AI_SPEAKING'),
('LEARNER_AI_CONVERSATION', 'LEARNER_SIDEBAR', 'Hội thoại AI', 'AI Conversation', '/ai-tutor', 'bot', 120, TRUE, TRUE, FALSE, 'AI_TUTOR');

INSERT INTO site_banners
    (code, placement, eyebrow_vi, eyebrow_en, title_vi, title_en, description_vi, description_en,
     image_url, image_alt_vi, image_alt_en, photo_label_vi, photo_label_en, action_type,
     action_label_vi, action_label_en, action_target, display_order, enabled, feature_key)
VALUES
('GUEST_PHO', 'GUEST_HOME_HERO', 'Ẩm thực Việt Nam · Phở', 'Vietnamese cuisine · Phở',
 'Khám phá Việt Nam qua từng hương vị.', 'Discover Vietnam, One Flavor at a Time.',
 'Tìm hiểu ngôn ngữ và văn hóa Việt Nam qua những hương vị quen thuộc.', 'Explore Vietnamese language and culture through the flavors of everyday life.',
 '/design/hero/slide1_welcome.jpg', 'Hai tô phở bò Việt Nam ăn cùng rau thơm và chanh', 'Two authentic bowls of phở bò served with Vietnamese herbs and lime',
 'Phở bò · Thành phố Hồ Chí Minh', 'Phở bò · Ho Chi Minh City', 'INTERNAL_ROUTE', 'Bắt đầu học', 'Start Learning', '/welcome', 10, TRUE, 'GUEST_HOME_HERO'),
('GUEST_TET', 'GUEST_HOME_HERO', 'Tết cổ truyền Việt Nam', 'Vietnamese Lunar New Year · Tết',
 'Khám phá nét đẹp Tết Việt.', 'Experience the Magic of Vietnamese Tết.',
 'Tìm hiểu những phong tục và lời chúc ý nghĩa trong ngày Tết cổ truyền.', 'Discover meaningful traditions and expressions that bring Vietnamese families together.',
 '/design/hero/slide2_speaking.jpg', 'Gia đình Việt Nam quây quần gói bánh tét đón Tết', 'A Vietnamese family gathering to prepare bánh tét for Lunar New Year',
 'Truyền thống gia đình ngày Tết · Việt Nam', 'Tết family tradition · Vietnam', 'INTERNAL_ROUTE', 'Khám phá tiếng Việt', 'Explore Vietnamese', '/welcome', 20, TRUE, 'GUEST_HOME_HERO'),
('GUEST_MID_AUTUMN', 'GUEST_HOME_HERO', 'Lễ hội truyền thống · Tết Trung Thu', 'Mid-Autumn Festival · Tết Trung Thu',
 'Rực rỡ sắc màu Tết Trung Thu.', 'Celebrate the Colors of Mid-Autumn Festival.',
 'Khám phá tiếng Việt qua những lễ hội và phong tục đầy màu sắc.', 'Learn Vietnamese through the joyful festivals and traditions of Vietnam.',
 '/design/hero/slide3_culture.jpg', 'Những chiếc đèn ông sao đầy màu sắc cho Tết Trung Thu Việt Nam', 'Colorful handmade star lanterns for Vietnamese Mid-Autumn Festival',
 'Đèn ông sao · Kon Tum', 'Star lanterns · Kon Tum', 'INTERNAL_ROUTE', 'Khám phá thêm', 'Discover More', '/welcome', 30, TRUE, 'GUEST_HOME_HERO'),
('GUEST_HOI_AN', 'GUEST_HOME_HERO', 'Di sản Việt Nam · Hội An', 'Vietnamese heritage · Hội An',
 'Ngôn ngữ kết nối những câu chuyện văn hóa.', 'A Language Full of Culture and Stories.',
 'Bắt đầu hành trình học tiếng Việt và khám phá những nét đẹp của đất nước, con người Việt Nam.', 'Begin your Vietnamese journey and discover the places, people, and traditions behind every word.',
 '/design/hero/slide4_journey.jpg', 'Phố cổ và dòng sông Hội An rực sáng bởi đèn lồng về đêm', 'Hoi An ancient town and river illuminated by lanterns at night',
 'Phố cổ Hội An · Việt Nam', 'Hội An ancient town · Vietnam', 'INTERNAL_ROUTE', 'Bắt đầu hành trình', 'Begin Your Journey', '/welcome', 40, TRUE, 'GUEST_HOME_HERO'),

('LEARNER_BUNCHA', 'LEARNER_HOME_HERO', 'Ẩm thực Việt Nam · Hà Nội', 'Vietnamese Cuisine · Hà Nội',
 'Khám phá Việt Nam qua từng hương vị', 'Discover Vietnam Through Its Flavors',
 'Học tiếng Việt và trải nghiệm nét đẹp truyền thống của ẩm thực Việt Nam đậm đà bản sắc.', 'Learn Vietnamese while exploring the delicious traditions of Vietnamese cuisine.',
 '/design/hero/hero_real_cafe_study.jpg', 'Bún chả Hà Nội truyền thống với thịt nướng, bún tươi, rau sống và nước chấm', 'Authentic Hanoi bún chả with grilled pork, rice noodles, fresh herbs, and dipping sauce',
 NULL, NULL, 'CONTINUE_LEARNING', 'Tiếp tục học', 'Continue Learning', NULL, 10, TRUE, 'LEARNER_HOME_HERO'),
('LEARNER_MAUSOLEUM', 'LEARNER_HOME_HERO', 'Lịch sử & Di sản · Hà Nội', 'History & Heritage · Hà Nội',
 'Khám phá lịch sử và di sản Việt Nam', 'Explore Vietnam''s History and Heritage',
 'Khám phá tiếng Việt qua các dấu ấn lịch sử và danh lam văn hóa tiêu biểu của Việt Nam.', 'Discover Vietnamese language through the history and cultural landmarks of Vietnam.',
 '/design/hero/slide3_culture.jpg', 'Lăng Chủ tịch Hồ Chí Minh tại Quảng trường Ba Đình, Hà Nội', 'Ho Chi Minh Mausoleum in Ba Đình Square, Hanoi',
 NULL, NULL, 'INTERNAL_ROUTE', 'Khám phá di sản', 'Explore Landmarks', '/culture', 20, TRUE, 'LEARNER_HOME_HERO'),
('LEARNER_TET', 'LEARNER_HOME_HERO', 'Tết Cổ Truyền · Tết Nguyên Đán', 'Lunar New Year · Tết Nguyên Đán',
 'Hòa mình vào không khí Tết Việt', 'Celebrate the Spirit of Vietnamese Tết',
 'Tìm hiểu phong tục, lời chúc và nét đẹp văn hóa ngày lễ quan trọng nhất trong năm.', 'Discover the traditions, greetings, and customs of Vietnam''s most important holiday.',
 '/design/hero/slide4_journey.jpg', 'Mâm cỗ Tết truyền thống với bánh chưng, giò lụa và món ăn ngày Tết', 'Traditional Vietnamese Tết spread with square bánh chưng, giò lụa, and festive accompaniments',
 NULL, NULL, 'CONTINUE_LEARNING', 'Khám phá truyền thống', 'Explore Traditions', NULL, 30, TRUE, 'LEARNER_HOME_HERO'),
('LEARNER_MID_AUTUMN', 'LEARNER_HOME_HERO', 'Lễ hội truyền thống · Trung Thu', 'Traditional Festivals · Trung Thu',
 'Trải nghiệm sắc màu Tết Trung Thu', 'Experience the Magic of Mid-Autumn Festival',
 'Khám phá văn hóa Việt Nam qua lễ hội rực rỡ sắc màu và những phong tục đầy ý nghĩa.', 'Explore Vietnamese culture through colorful festivals and meaningful traditions.',
 '/design/hero/slide5_aitutor.jpg', 'Đêm hội Tết Trung Thu Việt Nam với đoàn múa lân sư rồng và đèn lồng truyền thống', 'Authentic Vietnamese Trung Thu celebration with traditional lion dances and festival lanterns',
 NULL, NULL, 'INTERNAL_ROUTE', 'Khám phá lễ hội', 'Explore Festivals', '/ai-tutor', 40, TRUE, 'LEARNER_HOME_HERO');
