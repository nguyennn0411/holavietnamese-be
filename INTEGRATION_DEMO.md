# Bộ dữ liệu demo bài học, từ vựng và hành trình

## Khám phá Việt Nam — luồng mới của An

Khám phá mở tự do các điểm đã xuất bản cho người dùng đã đăng nhập, không phụ thuộc Course/Lesson. Trang chi tiết có Văn hóa, Ngôn ngữ vùng miền và Thực hành; không bắt đầu/hoàn thành điểm, tính tiến độ hay cấp dấu mới. Course và các kết quả học của module khác được giữ nguyên.

- `GET /api/destinations/{id}/content`: chỉ đọc từ, biến thể, ID bài văn hóa và scenario khả dụng; không ghi lịch sử học.
- `GET/PUT /api/admin/destinations/{id}/explore-content`: quản lý liên kết bằng `vocabularyIds` (số), `cultureIds` (chuỗi), `scenarioIds` (chuỗi). Giữ phân quyền quản lý hiện có; không thay cấu hình quyền chung.
- Migration `V29` chỉ thêm bảng tham chiếu nội dung; không xóa bảng, dữ liệu học, Lesson hoặc Passport cũ. Khởi động lại Backend để Flyway áp dụng sau khi cập nhật source; Docker cần build lại image.
- Endpoint ghi Journey cũ (`start`, `complete`, cấu hình journey, liên kết Lesson cũ) và endpoint nội dung Journey của học viên trả HTTP 410. `/admin/journey` chuyển về quản lý điểm đến. `LegacyJourneyReadController` chỉ giữ các API đọc mà Dashboard/Achievements và script kiểm tra liên kết của đồng đội còn sử dụng. Endpoint Passport chỉ đọc các dấu lịch sử.
- Culture hiện được mở qua thư viện Frontend của Tuyển (`cultureService`), chưa có API dữ liệu Culture thật trong source này. Explore chỉ lưu ID tham chiếu, không sao chép nội dung. ID bị thiếu khỏi thư viện được ẩn ở phía học viên.
- Scenario phải tồn tại trong DB, đang `ACTIVE` và có đúng ID mà màn scenario hiện tại của Tuyển hỗ trợ. Không dùng fallback sang scenario đầu tiên khi ID không khớp. Màn hội thoại/đánh giá của Tuyển được giữ nguyên; chưa coi phần thực hành hiện có là đánh giá AI thật.
- Kiểm tra hồi quy: `DestinationExploreIntegrationTests`, `RegionalVocabularyIntegrationTests` và `qa/explore-contract.test.mjs`. Thử lưu liên kết trong chi tiết điểm đến rồi mở bằng tài khoản Learner chưa học bài nào.

Các mục demo bên dưới mô tả bộ liên kết/lịch sử cũ, không còn là điều kiện mở khóa của trang Khám phá mới. Không chạy seed chỉ để dùng chức năng Khám phá và không xóa lịch sử cũ khi chuyển luồng.

Khóa học **Hành trình Việt Nam — Demo liên kết**, mã `DEMO-VIETNAM-LINKS`, slug `demo-hanh-trinh-tieng-viet`.

| Chặng | Bài học | Từ vựng dùng chung |
| --- | --- | --- |
| Hà Nội | Chào hỏi tại Hà Nội; Gọi món ở Hà Nội | xin chào, cảm ơn, tạm biệt, phở, nước, thực đơn |
| Hội An | Hỏi đường tại Hội An; Mua sắm ở Hội An | ở đâu, rẽ trái, đi thẳng, bao nhiêu, đắt, giảm giá |
| TP.HCM | Di chuyển tại TP.HCM; Nhận phòng và kết thúc hành trình | xe buýt, vé, sân bay, đặt phòng, chìa khóa, hẹn gặp lại |

Mỗi bài có tình huống, ba từ với câu ví dụ, hội thoại và quiz ba câu. Quiz cuối khóa có sáu câu, dùng lại câu hỏi của từng bài; điểm đạt là 70%. Khóa học được tạo dưới Content Manager, gửi duyệt và phê duyệt qua service Admin. Tất cả ID được lấy từ database, không cố định ID giữa các máy.

## Dữ liệu đã nạp trên máy này

- Khóa học: `http://localhost:5173/courses/17`.
- Hà Nội: địa điểm `1`, bài `287–288`, từ `1–6`.
- Hội An: địa điểm `2`, bài `289–290`, từ `7–12`.
- TP.HCM: địa điểm `3`, bài `291–292`, từ `13–18`.
- Tài khoản `learner` đã ghi danh khóa mẫu; chưa hoàn thành bài nào, Hà Nội đang mở và hai chặng sau còn khóa.

Tài khoản môi trường `dev-demo` đã kiểm tra đăng nhập:

| Vai trò | Tài khoản | Mật khẩu |
| --- | --- | --- |
| Admin | admin | admin123 |
| Content Manager | contentmanager | contentmanager123 |
| Học viên | learner | learner123 |

Chạy `node scripts/verify-integration-demo.cjs` để đọc lại ID thực tế và kiểm tra liên kết trên máy của từng người. Script cho phép thay tài khoản bằng các biến `DEMO_ADMIN_USERNAME`, `DEMO_ADMIN_PASSWORD`, `DEMO_MANAGER_USERNAME`, `DEMO_MANAGER_PASSWORD`, `DEMO_LEARNER_USERNAME`, `DEMO_LEARNER_PASSWORD` và đổi backend bằng `DEMO_API_URL`. Script chỉ kiểm tra và đăng nhập, không hoàn thành bài hoặc đặt lại tiến độ.

Bảng ID chi tiết đã kiểm chứng được lưu tại `D:\DO_AN\outputs\integration-demo-20261020\manifest.json`. Để ghép phần của nhóm, dùng seed hoặc API theo mã/slug và ID trả về, không chép cố định những ID cục bộ này sang database khác.

## Nạp dữ liệu

Tại thư mục BE, sử dụng file `.env.mysql` hiện có:

```powershell
docker compose --env-file .env.mysql -f compose.yml -f compose.demo.yml -f compose.integration-demo.yml up -d --build --wait
```

`APP_INTEGRATIONDEMO_SEED_ENABLED=true` bật bộ nạp. Có thể chạy lại: bộ nạp kiểm tra cấu trúc khóa học đã tồn tại rồi dùng lại, không nhân đôi. Toàn bộ seed chạy trong một transaction. Dữ liệu khóa học cũ và tiến độ học không bị sửa. Những điểm hành trình cũ được giữ nguyên thứ tự; ba điểm demo được thêm ở cuối. Nếu có hành trình trước đó, phải hoàn thành các chặng trước hoặc thống nhất lại thứ tự tại `/admin/journey`.

Nguồn biên soạn nằm ở `src/main/resources/demo/integration-journey.json`. File chứa nội dung mẫu, không chứa trạng thái hay điểm học viên. Ảnh điểm đến dùng URL từ bộ destination demo có sẵn; cần kết nối mạng để tải ảnh.

## Kịch bản kiểm tra liên kết cho nhóm

1. Admin mở `/admin/courses`, tìm mã `DEMO-VIETNAM-LINKS`, xem tác giả, thông tin duyệt và sáu bài học.
2. Mở `/admin/destinations`, tìm các điểm có hậu tố “Demo liên kết”. Tab nội dung của mỗi điểm có đúng hai bài học và sáu từ. `/admin/journey` có thứ tự Hà Nội → Hội An → TP.HCM.
3. Học viên mở `/courses`, vào khóa demo và ghi danh. Mở phần Khám phá/hành trình: Hà Nội được mở, hai chặng sau bị khóa.
4. Tại Hà Nội, chọn bài Chào hỏi. Mở từ `xin chào` trong danh mục chung hoặc bấm Lưu từ. Sổ tay lưu đồng thời `lessonId` của bài và `masterVocabularyId` của từ chung.
5. Từ địa điểm Hà Nội, mở cùng mục từ `xin chào`: ID phải trùng từ đã lưu từ bài học. Lưu lại cùng từ phải được báo đã tồn tại. Vào sổ tay, lọc theo khóa/bài học và ôn tập flashcard.
6. Hoàn thành hoạt động và đạt quiz của cả hai bài Hà Nội, quay lại điểm Hà Nội và hoàn tất chặng. Hộ chiếu có một dấu, Hội An được mở. Làm tương tự cho Hội An và TP.HCM; bài cuối cần đạt cả quiz bài học và quiz cuối khóa.

Các API nối phần của nhóm:

| Liên kết | API |
| --- | --- |
| Chọn bài học/từ cho điểm đến | `PUT /api/admin/destinations/{id}/content` với `lessonIds`, `vocabularyIds` |
| Thứ tự chặng | `PUT /api/admin/journey` |
| Nội dung học của điểm đến | `GET /api/me/journey/destinations/{id}/content` |
| Bài học và hoạt động | `GET /api/lessons/{id}/activities` |
| Danh mục từ dùng chung | `GET /api/vocabulary/{id}` |
| Lưu từ từ bài học | `POST /api/me/vocabulary` với `lessonId`, `masterVocabularyId`, `word`, `meaning` |
| Hoàn thành chặng / hộ chiếu | `POST /api/me/journey/destinations/{id}/complete`, `GET /api/me/journey/passport` |

Điều kiện hoàn thành do backend tính từ `lesson_progress`: hoàn thành mọi bài đã liên kết với chặng trước khi nhận dấu và mở chặng sau. Ghi danh khóa học trước khi học hoặc lưu từ theo bài.

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

## Kết quả kiểm tra

- Backend đã khởi động với MySQL hiện tại; Flyway validation qua 25 migration và áp dụng thành công 9 migration mới.
- V1–V16 giống hoàn toàn bản sao trước chỉnh sửa; 16 khóa học cũ vẫn còn, thêm khóa demo thành tổng 17 khóa.
- Hai kiểm thử tích hợp trên MySQL tạm qua: seed lặp lại/giữ hành trình cũ, và luồng học thật → quiz → lưu từ theo bài → hoàn thành ba chặng → ba dấu hộ chiếu. MySQL tạm dùng RAM, không gắn volume thật, đã dừng sau kiểm thử. H2 không hỗ trợ cú pháp đa cột của V14 gốc nên các kiểm thử này phải dùng MySQL.
- 14 kiểm tra contract FE cho từ vựng/hành trình và build FE đã qua.
- Đã kiểm tra trên giao diện: đăng nhập học viên, ghi danh khóa 17, mở bài 287, từ bài học mở từ chung ID 1; điểm Hà Nội cũng trỏ đến cùng mục từ ID 1 và hai bài 287–288.
