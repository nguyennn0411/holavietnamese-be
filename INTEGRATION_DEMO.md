# Khám phá Việt Nam và lịch sử tích hợp

## Khám phá Việt Nam — luồng mới của An

Khám phá mở tự do các điểm đã xuất bản cho người dùng đã đăng nhập, không phụ thuộc Course/Lesson. Trang chi tiết có Văn hóa, Ngôn ngữ vùng miền và Thực hành; không bắt đầu/hoàn thành điểm, tính tiến độ hay cấp dấu mới. Course và các kết quả học của module khác được giữ nguyên.

- `GET /api/destinations/{id}/content`: chỉ đọc từ, biến thể, ID bài văn hóa và scenario khả dụng; không ghi lịch sử học.
- `GET/PUT /api/admin/destinations/{id}/explore-content`: quản lý liên kết bằng `vocabularyIds` (số), `cultureIds` (chuỗi), `scenarioIds` (chuỗi). Giữ phân quyền quản lý hiện có; không thay cấu hình quyền chung.
- Migration `V29` chỉ thêm bảng tham chiếu nội dung; không xóa bảng, dữ liệu học, Lesson hoặc Passport cũ. Khởi động lại Backend để Flyway áp dụng sau khi cập nhật source; Docker cần build lại image.
- Các API Journey/Passport cũ và báo cáo tiến độ điểm đến giả đã được gỡ cùng service, DTO và repository không còn dùng. `/api/users/progress` chỉ phục vụ dữ liệu học tập, không trả `journeyCity` hay `journeyProgress`. Achievements giữ huy hiệu học tập; My Learning dẫn sang Explore để khám phá tự do. `/admin/journey` chỉ là URL chuyển hướng về quản lý điểm đến.
- Giữ nguyên migration đã chạy và các bảng lịch sử trong DB; ứng dụng không đọc hoặc ghi các bảng Journey/Passport cũ. Liên kết từ vựng và tham chiếu nội dung của Explore vẫn được sử dụng.
- Culture hiện được mở qua thư viện Frontend của Tuyển (`cultureService`), chưa có API dữ liệu Culture thật trong source này. Explore chỉ lưu ID tham chiếu, không sao chép nội dung. ID bị thiếu khỏi thư viện được ẩn ở phía học viên.
- Scenario phải tồn tại trong DB, đang `ACTIVE` và có đúng ID mà màn scenario hiện tại của Tuyển hỗ trợ. Không dùng fallback sang scenario đầu tiên khi ID không khớp. Màn hội thoại/đánh giá của Tuyển được giữ nguyên; chưa coi phần thực hành hiện có là đánh giá AI thật.
- Kiểm tra hồi quy: `DestinationExploreIntegrationTests`, `RegionalVocabularyIntegrationTests` và `qa/explore-contract.test.mjs`. Thử lưu liên kết trong chi tiết điểm đến rồi mở bằng tài khoản Learner chưa học bài nào.

Các bộ demo tích hợp cũ đã được gỡ. Bộ seed điểm đến hiện tại đồng bộ `demo/destinations.json` theo slug khi khởi động nếu bật cấu hình seed; không tạo từ vựng hoặc liên kết nội dung. Vocabulary dùng quản lý thủ công và import Excel, không có bộ từ mẫu tự nạp. Các mục từ đã nhập trước đây vẫn được giữ nguyên.

## Đối chiếu migration MySQL ngày 20/10/2026

Database cục bộ đã chạy V1–V16; cả 16 checksum được đối chiếu và khớp file hiện có. Giữ nguyên toàn bộ những file đó. Nhánh backup mới có số phiên bản bị trùng nên các migration chưa chạy được chuyển như sau:

| File trước đồng bộ | Phiên bản đang dùng |
| --- | --- |
| V12 vietnam journey | V17 |
| V13 link notebook master vocabulary | V18 |
| V14 add hearts to users | V19 |
| V14 vocabulary review | V20 |
| V15 add api key to ai settings | V21 |
| V15 destination content links | V22 |
| V18 regional vocabulary | V23 |
| V19 content import history | V24 |
| V20 dictionary content and provenance | V25 |

Ba bản AI trùng (`V13__align_ai_schema`, `V16__ai_tutor_and_roleplay`, `V17__align_ai_schema`) được đưa ra ngoài thư mục migration vì các thao tác tương ứng đã nằm trong V13 và V14 đã chạy. Bản sao database và toàn bộ migration trước sửa nằm tại `D:\DO_AN\outputs\integration-demo-20261020`. Không chạy Repair và không chỉnh sửa `flyway_schema_history` trực tiếp.

Khi ghép sang máy khác, đối chiếu lịch sử Flyway của database đó trước khi dùng thứ tự này. Nếu máy đó đã chạy migration từ nhánh backup với số cũ, không đổi tên file đã chạy hoặc dùng Repair tự động.

Commit AI Tutor mới trên remote bổ sung `context_summary` cho `ai_sessions`. Thay đổi chưa chạy này dùng **V26** để giữ nguyên V1–V25 đã có. Các bản hearts/API key được giữ ở V19/V21 theo lịch sử MySQL cục bộ. `FlywayConfig` sử dụng migrate với validation mặc định, không tự Repair khi khởi động.
