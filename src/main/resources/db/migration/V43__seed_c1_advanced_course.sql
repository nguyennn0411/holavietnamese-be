-- Give advanced learners a real C1 course in the public catalog.
INSERT INTO courses (
  code, slug, title, title_vi, description, description_vi, level,
  estimated_duration, learning_outcomes, is_free, status
)
SELECT
  'HOLA-C1-ACADEMIC',
  'tieng-viet-c1-lap-luan-hoc-thuat',
  'Advanced Vietnamese: Academic Argumentation',
  'Tiếng Việt C1: Lập luận học thuật',
  'Develop nuanced arguments, evaluate evidence, and present complex ideas naturally in advanced Vietnamese.',
  'Rèn luyện cách xây dựng lập luận, đánh giá bằng chứng và trình bày các ý tưởng phức tạp bằng tiếng Việt tự nhiên.',
  'C1',
  180,
  'Build nuanced arguments; evaluate sources critically; present and defend an academic viewpoint',
  TRUE,
  'PUBLISHED'
WHERE NOT EXISTS (
  SELECT 1 FROM courses WHERE code = 'HOLA-C1-ACADEMIC'
);

INSERT INTO course_units (
  course_id, code, title_en, title_vi, description_en, description_vi, sort_order, status
)
SELECT
  c.id,
  'HOLA-C1-ACADEMIC-U1',
  'Academic reasoning and expression',
  'Lập luận và diễn đạt học thuật',
  'Use precise language to analyze, synthesize, and defend complex viewpoints.',
  'Sử dụng ngôn ngữ chính xác để phân tích, tổng hợp và bảo vệ quan điểm phức tạp.',
  1,
  'PUBLISHED'
FROM courses c
WHERE c.code = 'HOLA-C1-ACADEMIC'
  AND NOT EXISTS (
    SELECT 1 FROM course_units WHERE code = 'HOLA-C1-ACADEMIC-U1'
  );

INSERT INTO lessons (
  course_id, unit_id, unit_sort_order, code, slug, title, title_vi,
  description, description_vi, lesson_type, estimated_duration, lesson_order,
  content, learning_objective, learning_objective_vi, published, publication_status
)
SELECT
  c.id,
  u.id,
  1,
  'HOLA-C1-ACADEMIC-L1',
  'c1-xay-dung-lap-luan-chat-che',
  'Building a rigorous argument',
  'Xây dựng lập luận chặt chẽ',
  'Organize a complex claim with clear premises, evidence, counterarguments, and conclusions.',
  'Tổ chức một luận điểm phức tạp bằng tiền đề, bằng chứng, phản biện và kết luận rõ ràng.',
  'NORMAL',
  55,
  1,
  '<h2>Xây dựng lập luận chặt chẽ</h2><p>Một lập luận học thuật thuyết phục cần nêu rõ luận điểm, giải thích cơ sở, đưa ra bằng chứng đáng tin cậy và dự liệu ý kiến phản biện.</p><h3>Khung diễn đạt</h3><p>Trước hết, cần thừa nhận rằng... Tuy nhiên, xét trên phương diện... Vì vậy, có thể khẳng định rằng...</p><h3>Thực hành</h3><p>Hãy chọn một vấn đề xã hội, viết luận điểm chính và bổ sung ít nhất hai bằng chứng cùng một ý kiến phản biện.</p>',
  'Structure and defend a nuanced academic argument in Vietnamese.',
  'Xây dựng và bảo vệ một lập luận học thuật có chiều sâu bằng tiếng Việt.',
  TRUE,
  'PUBLISHED'
FROM courses c
JOIN course_units u ON u.course_id = c.id
WHERE c.code = 'HOLA-C1-ACADEMIC'
  AND u.code = 'HOLA-C1-ACADEMIC-U1'
  AND NOT EXISTS (
    SELECT 1 FROM lessons WHERE code = 'HOLA-C1-ACADEMIC-L1'
  );

INSERT INTO lessons (
  course_id, unit_id, unit_sort_order, code, slug, title, title_vi,
  description, description_vi, lesson_type, estimated_duration, lesson_order,
  content, learning_objective, learning_objective_vi, published, publication_status
)
SELECT
  c.id,
  u.id,
  2,
  'HOLA-C1-ACADEMIC-L2',
  'c1-danh-gia-nguon-va-bang-chung',
  'Evaluating sources and evidence',
  'Đánh giá nguồn và bằng chứng',
  'Distinguish reliable evidence from weak generalizations and biased claims.',
  'Phân biệt bằng chứng đáng tin cậy với khái quát thiếu cơ sở và nhận định thiên lệch.',
  'NORMAL',
  60,
  2,
  '<h2>Đánh giá nguồn và bằng chứng</h2><p>Ở trình độ C1, người học không chỉ hiểu thông tin mà còn cần nhận diện độ tin cậy, phạm vi áp dụng và giới hạn của từng nguồn.</p><h3>Câu dẫn học thuật</h3><p>Theo số liệu được công bố... Kết quả này chỉ ra rằng... Dẫu vậy, kết luận trên cần được xem xét thận trọng vì...</p><h3>Thực hành</h3><p>So sánh hai nguồn viết về cùng một chủ đề và giải thích nguồn nào có sức thuyết phục cao hơn.</p>',
  'Assess the reliability, relevance, and limitations of evidence.',
  'Đánh giá độ tin cậy, tính liên quan và giới hạn của bằng chứng.',
  TRUE,
  'PUBLISHED'
FROM courses c
JOIN course_units u ON u.course_id = c.id
WHERE c.code = 'HOLA-C1-ACADEMIC'
  AND u.code = 'HOLA-C1-ACADEMIC-U1'
  AND NOT EXISTS (
    SELECT 1 FROM lessons WHERE code = 'HOLA-C1-ACADEMIC-L2'
  );

INSERT INTO lessons (
  course_id, unit_id, unit_sort_order, code, slug, title, title_vi,
  description, description_vi, lesson_type, estimated_duration, lesson_order,
  content, learning_objective, learning_objective_vi, published, publication_status
)
SELECT
  c.id,
  u.id,
  3,
  'HOLA-C1-ACADEMIC-L3',
  'c1-trinh-bay-va-bao-ve-quan-diem',
  'Presenting and defending a viewpoint',
  'Trình bày và bảo vệ quan điểm',
  'Deliver a coherent presentation and respond diplomatically to challenging questions.',
  'Trình bày mạch lạc và phản hồi khéo léo trước những câu hỏi mang tính thử thách.',
  'NORMAL',
  65,
  3,
  '<h2>Trình bày và bảo vệ quan điểm</h2><p>Một phần trình bày ở trình độ C1 cần mạch lạc, có chuyển ý tự nhiên và thể hiện khả năng điều chỉnh sắc thái khi trao đổi.</p><h3>Cách phản hồi</h3><p>Tôi hiểu mối quan ngại đó. Tuy nhiên... Nếu nhìn từ góc độ khác... Điểm cần nhấn mạnh ở đây là...</p><h3>Thực hành</h3><p>Chuẩn bị bài nói ba phút, sau đó tự đặt hai câu hỏi phản biện và trả lời bằng ngôn ngữ lịch sự, chính xác.</p>',
  'Present a complex viewpoint and respond to counterarguments diplomatically.',
  'Trình bày quan điểm phức tạp và phản hồi ý kiến trái chiều một cách khéo léo.',
  TRUE,
  'PUBLISHED'
FROM courses c
JOIN course_units u ON u.course_id = c.id
WHERE c.code = 'HOLA-C1-ACADEMIC'
  AND u.code = 'HOLA-C1-ACADEMIC-U1'
  AND NOT EXISTS (
    SELECT 1 FROM lessons WHERE code = 'HOLA-C1-ACADEMIC-L3'
  );
