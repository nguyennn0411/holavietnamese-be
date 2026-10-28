-- Sync the three roleplay scenarios that already exist in the frontend catalog.
-- The conditional inserts keep existing admin-edited scenarios untouched.

INSERT INTO ai_scenarios (
    id, title, level, topic, destination, difficulty, duration, thumbnail, cover_image, overview,
    ai_role_name, ai_role_title, ai_role_avatar, ai_role_tone, ai_role_initial_greeting,
    learner_role_title, learner_role_context, objectives_json, vocabulary_hints_json,
    cultural_tip, sample_hints_json, max_turns, status
)
SELECT
    'checkin-khach-san-da-nang',
    'Nhận phòng khách sạn & Hỏi dịch vụ tại Đà Nẵng',
    'B1',
    'hospitality',
    'Đà Nẵng',
    'Trung bình',
    '8-12 phút',
    'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=800&q=80',
    'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=1200&q=80',
    'Bạn vừa đến khách sạn 4 sao ven biển Mỹ Khê Đà Nẵng sau chuyến bay dài và làm thủ tục check-in tại quầy lễ tân.',
    'Lễ tân Như Ý',
    'Nhân viên lễ tân khách sạn Đà Nẵng chuyên nghiệp, chu đáo',
    '🛎️',
    'Lịch thiệp, chuẩn mực dịch vụ, giọng miền Trung nhẹ nhàng dễ thương',
    'Dạ em chào quý khách! Chào mừng anh/chị đã đến với Danang Sun Beach Hotel. Em có thể hỗ trợ anh/chị nhận phòng hoặc ký gửi hành lý ạ?',
    'Khách du lịch đã đặt phòng trực tuyến',
    'Cung cấp mã đặt phòng, đề xuất đổi sang phòng tầng cao ngắm biển, hỏi thời gian buffet sáng và xin tư vấn thuê xe máy.',
    '[{"id":"task-1","label":"Cung cấp tên và mã đặt phòng kèm giấy tờ tùy thân"},{"id":"task-2","label":"Yêu cầu phòng tầng cao có ban công hướng biển"},{"id":"task-3","label":"Hỏi giờ phục vụ ăn sáng và mật khẩu WiFi"},{"id":"task-4","label":"Hỏi thủ tục thuê xe máy vi vu bán đảo Sơn Trà"}]',
    '[{"word":"Tôi đã đặt phòng qua mạng","meaning":"I have booked a room online"},{"word":"Phòng ban công hướng biển","meaning":"Balcony room with ocean view"},{"word":"Buffet sáng phục vụ từ mấy giờ?","meaning":"What time is the breakfast buffet served?"},{"word":"Khách sạn có dịch vụ thuê xe máy không?","meaning":"Does the hotel offer motorbike rental service?"}]',
    'Người Đà Nẵng nổi tiếng thân thiện và mến khách bậc nhất cả nước, luôn sẵn sàng hướng dẫn bạn các quán hải sản người bản địa hay ăn.',
    '["Em đã đặt phòng tên là...","Anh/chị có thể cho em phòng tầng cao nhìn ra biển không ạ?","Buffet sáng phục vụ từ mấy giờ ạ?"]',
    12,
    'ACTIVE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_scenarios WHERE id = 'checkin-khach-san-da-nang');

INSERT INTO ai_scenarios (
    id, title, level, topic, destination, difficulty, duration, thumbnail, cover_image, overview,
    ai_role_name, ai_role_title, ai_role_avatar, ai_role_tone, ai_role_initial_greeting,
    learner_role_title, learner_role_context, objectives_json, vocabulary_hints_json,
    cultural_tip, sample_hints_json, max_turns, status
)
SELECT
    'ra-mat-gia-dinh-ban',
    'Gặp gỡ và chào hỏi gia đình bạn người Việt',
    'B2',
    'social',
    'Toàn quốc',
    'Nâng cao',
    '10-15 phút',
    'https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=800&q=80',
    'https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=1200&q=80',
    'Bạn được một người bạn thân mời về thăm nhà dùng cơm trưa nhân dịp cuối tuần. Đây là lần đầu bạn gặp mặt bố mẹ của bạn mình.',
    'Bác Hai (Bố của bạn)',
    'Chủ nhà nồng hậu, quý mến bạn của con cái',
    '👨',
    'Ấm áp, ân cần, coi trọng lễ nghĩa gia phong',
    'Dạ chào cháu! Bác nghe Nam kể nhiều về cháu lắm rồi, hôm nay mới có dịp gặp mặt. Vào nhà uống nước xơi trầu cho mát nhé cháu!',
    'Người bạn ngoại quốc đến thăm nhà dùng cơm',
    'Chào hỏi lễ phép với kính ngữ chuẩn mực, trao món quà nhỏ kèm lời chúc, khen ngợi món ăn và mời cơm đúng phép tắc.',
    '[{"id":"task-1","label":"Chào hỏi bằng kính ngữ Dạ con chào hai bác ạ"},{"id":"task-2","label":"Tặng quà lưu niệm và giải thích ý nghĩa lịch sự"},{"id":"task-3","label":"Mời cơm người lớn trước khi cầm đũa"},{"id":"task-4","label":"Cảm ơn lòng hiếu khách trước khi xin phép ra về"}]',
    '[{"word":"Dạ, con chào hai bác ạ!","meaning":"Polite greeting to the host parents"},{"word":"Con có chút hoa quả biếu hai bác","meaning":"I have some fruits as a gift for you two"},{"word":"Con mời hai bác xơi cơm","meaning":"Polite invitation to eat before starting meal"},{"word":"Bác gái nấu ăn ngon quá ạ!","meaning":"Auntie cooks so deliciously!"}]',
    'Khi đến thăm nhà người Việt, mang theo một giỏ trái cây tươi hoặc trà ngon làm quà biếu là hành động cực kỳ tinh tế và được quý trọng.',
    '["Dạ, con chào hai bác ạ!","Con có chút quà nhỏ biếu hai bác ạ.","Con mời hai bác xơi cơm ạ!"]',
    15,
    'ACTIVE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_scenarios WHERE id = 'ra-mat-gia-dinh-ban');

INSERT INTO ai_scenarios (
    id, title, level, topic, destination, difficulty, duration, thumbnail, cover_image, overview,
    ai_role_name, ai_role_title, ai_role_avatar, ai_role_tone, ai_role_initial_greeting,
    learner_role_title, learner_role_context, objectives_json, vocabulary_hints_json,
    cultural_tip, sample_hints_json, max_turns, status
)
SELECT
    'phong-van-xin-viec-cong-so',
    'Phỏng vấn xin việc tại doanh nghiệp Việt Nam',
    'C1',
    'work',
    'Hà Nội / TP.HCM',
    'Thử thách',
    '12-18 phút',
    'https://images.unsplash.com/photo-1521737604893-d14cc237f11d?auto=format&fit=crop&w=800&q=80',
    'https://images.unsplash.com/photo-1521737604893-d14cc237f11d?auto=format&fit=crop&w=1200&q=80',
    'Bạn đang tham gia vòng phỏng vấn chuyên sâu cho vị trí Chuyên viên Hợp tác Quốc tế tại một tập đoàn công nghệ lớn ở Việt Nam.',
    'Chị Lan Hương (Giám đốc Nhân sự)',
    'Nhà tuyển dụng chuyên nghiệp, đặt câu hỏi logic và tình huống thực tế',
    '💼',
    'Trang trọng, chuyên nghiệp, đánh giá cao sự lưu loát và hòa nhập văn hóa',
    'Chào bạn. Cảm ơn bạn đã quan tâm đến cơ hội nghề nghiệp tại công ty chúng tôi. Trước hết, bạn vui lòng giới thiệu ngắn gọn về bản thân và lý do bạn muốn làm việc tại Việt Nam nhé.',
    'Ứng viên sáng giá cho vị trí công việc chuyên nghiệp',
    'Trình bày bản thân bằng tiếng Việt chuẩn mực, nêu bật kỹ năng thế mạnh, cách thích nghi môi trường đa văn hóa và hỏi lại nhà tuyển dụng.',
    '[{"id":"task-1","label":"Tự giới thiệu bản thân và kinh nghiệm chuyên môn lưu loát"},{"id":"task-2","label":"Trình bày động lực làm việc và tình cảm với môi trường Việt Nam"},{"id":"task-3","label":"Trả lời câu hỏi xử lý bất đồng văn hóa trong làm việc nhóm"},{"id":"task-4","label":"Đặt câu hỏi thông minh về định hướng phát triển của công ty"}]',
    '[{"word":"Kinh nghiệm chuyên môn","meaning":"Professional expertise and experience"},{"word":"Thích ứng môi trường đa văn hóa","meaning":"Adapting to multicultural environment"},{"word":"Tinh thần làm việc nhóm","meaning":"Teamwork spirit and collaboration"},{"word":"Đóng góp vào sự phát triển","meaning":"Contribute to the growth/development"}]',
    'Trong văn hóa doanh nghiệp Việt Nam, sự tôn trọng đồng nghiệp, tinh thần đoàn kết và tính khiêm tốn được đánh giá cao ngang bằng với năng lực chuyên môn.',
    '["Tôi có kinh nghiệm chuyên môn trong lĩnh vực...","Tôi tin rằng kỹ năng của mình phù hợp với vị trí này.","Tôi muốn hỏi thêm về định hướng phát triển của công ty."]',
    18,
    'ACTIVE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM ai_scenarios WHERE id = 'phong-van-xin-viec-cong-so');
