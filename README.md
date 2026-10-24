# Hola Vietnamese Backend

Backend Spring Boot được tổ chức theo monolith phân lớp, sử dụng MySQL 8.4 và Flyway. Frontend là ứng dụng React riêng trong `FE/holavietnamese_fe`.

Module course/activity/grammar/question bank/quiz mới nằm trong package `learning`.
Các lesson đã xuất bản được học theo thứ tự tùy ý sau khi đăng ký course; bài tiên quyết không còn khóa truy cập. Điều kiện hoàn thành activity, quiz và course vẫn được kiểm tra khi ghi nhận tiến độ.
Chạy dữ liệu demo tùy chọn bằng `./start-demo.ps1`; xem báo cáo và quy trình kiểm tra ở `../MEMBER2_IMPLEMENTATION.md`.

## Cấu trúc

```text
src/main/java/com/sep490/backend/
├── BackendApplication.java
├── controller/          # REST endpoints
├── service/             # Nghiệp vụ và transaction
│   └── importing/       # Đọc và kiểm tra workbook Excel
├── repository/          # Truy vấn, lưu dữ liệu và khóa đồng thời
│   └── jpa/             # Spring Data JPA repositories
├── entity/              # JPA entities, giữ nguyên mapping bảng
│   └── enums/
├── dto/
│   ├── request/
│   ├── response/
│   ├── model/           # Snapshot dữ liệu và tính toán tiến độ
│   └── courseimport/    # Dữ liệu và hợp đồng workbook
├── mapper/              # Chuyển JPA entity sang model
├── config/              # Security, principal và cấu hình Spring
└── exception/           # Exception và REST error handler
```

Controller gọi service trực tiếp. Service gọi repository; các repository dùng Spring Data JPA hoặc JDBC.
Không còn lớp port/use-case interface và persistence adapter của cấu trúc cũ.
Các model bất biến và mapper được giữ để bảo toàn hành vi, không trả JPA entity trực tiếp qua API.
Transaction, phân quyền và URL API được giữ nguyên. Flyway dùng migration MySQL trong `src/main/resources/db/migration`.

## Chạy và kiểm thử

Yêu cầu Java 21 và Maven. Cache dependency dùng mặc định `%USERPROFILE%\.m2\repository`.

- Mở Docker Desktop (Linux containers), chạy `.\start-mysql.ps1 -StartBackend` bằng PowerShell 7 để chạy MySQL và BE trong Docker.
- Script tự tạo `.env.mysql` với mật khẩu ngẫu nhiên nếu chưa có. Không commit file này.
- Nếu chạy BE trong IntelliJ: chạy `.\start-mysql.ps1` để bật MySQL, rồi chạy `BackendApplication` với working directory là `BE`, hoặc `mvn spring-boot:run`.
- MySQL ở `localhost:3307`. BE Docker ở `http://localhost:18080` (biến `DOCKER_BACKEND_PORT`); chạy BE trực tiếp từ IntelliJ dùng `BACKEND_PORT` (máy hiện tại: 8088).
- Chạy `mvn clean test` với Java 21. Test mặc định dùng H2 riêng.
- MySQL dùng volume mới; dữ liệu SQL Server cũ chưa được chuyển tự động.

## Tài liệu

- [DEMO_DATA.md](DEMO_DATA.md): nạp lại toàn bộ dữ liệu nội dung/demo từ nhánh backup trên máy của nhóm.
- [MSU_OPENBOOKS_CONTENT.md](MSU_OPENBOOKS_CONTENT.md): tái tạo 22 khóa học mẫu Basic/Advanced Vietnamese từ dữ liệu nguồn đã commit.
- [INTEGRATION_DEMO.md](INTEGRATION_DEMO.md): luồng Khám phá Việt Nam hiện tại và lưu ý đối chiếu lịch sử migration.

### Từ vựng song ngữ và Excel

- Learner tra cứu qua `GET /api/vocabulary/search` với `keyword`, `page`, `size`. Tìm tên tiếng Việt hoặc thông tin nghĩa tiếng Anh đã lưu; không tự dịch. Chỉ hiển thị mục `PUBLISHED`.
- Admin tìm theo `keyword`, `status`, `partOfSpeech`. Không còn phân loại từ theo trình độ hoặc chủ đề. Các bảng/cột cũ được giữ trong DB và migration để bảo toàn lịch sử; ứng dụng không đọc/ghi chúng.
- Mẫu XLSX có sheet `VOCABULARY`. Header bắt buộc: `word`, `part_of_speech`. Mỗi nghĩa cần ít nhất một trường: `definition_vi`, `translation_en`, `definition_en`.
- Header đầy đủ: `word`, `part_of_speech`, `pronunciation`, `audio_url`, `status`, `meaning_order`, `definition_vi`, `translation_en`, `definition_en`, `usage_note`, `labels`, `example_order`, `example_vi`, `example_en`, `example_audio_url`.
- Ghi `word` và `part_of_speech` trên mỗi dòng. Các dòng cùng từ/từ loại được gom thành một mục. `meaning_order` (mặc định 0) phân biệt các nghĩa; `example_order` sắp xếp ví dụ, để trống thì nối tiếp. Các trường lặp có thể để trống; nếu giá trị khác nhau trong cùng mục/nghĩa, preview báo lỗi. `labels`: mỗi nhãn một dòng trong ô.
- Ví dụ cần `example_vi`; bản dịch và audio ví dụ là tùy chọn. Không tự tạo bản dịch còn thiếu. Trạng thái trống là `DRAFT`.
- Preview không ghi mục từ. Confirm kiểm tra lại và nhập toàn bộ trong một giao dịch; từ/từ loại đã tồn tại bị từ chối, không ghi đè. `importedRows` đếm số mục từ tạo mới, có thể ít hơn số dòng Excel.
- Nội dung, thông tin nguồn lịch sử và liên kết của các mục từ đã có vẫn được giữ. Không còn phương thức import JSONL hoặc dữ liệu pilot kèm source.

- [LEARNER_FEATURES.md](LEARNER_FEATURES.md): API học viên và kiểm thử.
- [COURSE_IMPORT.md](COURSE_IMPORT.md): import Excel dành cho ADMIN.
- [MYSQL_DOCKER.md](MYSQL_DOCKER.md): cấu hình MySQL Docker và kiểm thử trên MySQL.
