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
- [INTEGRATION_DEMO.md](INTEGRATION_DEMO.md): luồng Khám phá Việt Nam hiện tại và lưu ý đối chiếu lịch sử migration.

### Pilot từ điển tiếng Việt

- `python tools/prepare_dictionary_pilot.py --groups 200` đọc streaming raw JSONL gzip của Kaikki/Wiktextract, lọc tiếng Việt và tạo pilot 100–300 nhóm từ/từ loại trong `src/main/resources/dictionary/`. Script không kết nối DB. Cache dump nằm trong `.verification/`.
- Manifest lưu ngày dump/trích xuất, SHA-256, thống kê loại bỏ, giấy phép CC BY-SA 4.0 và thông báo chuẩn hóa. `LICENSE.txt` áp dụng cho nội dung từ điển, không đổi giấy phép phần mềm. Audio/hình và ví dụ có trích dẫn riêng bị loại; dữ liệu chưa được duyệt nội dung.
- Sau khi backup và triển khai migration V20, Admin vào trang import hiện có, chọn **Từ điển Wiktionary · JSONL**, tải pilot được cung cấp, xem preview rồi xác nhận. Chỉ file có checksum trong manifest triển khai mới được xác nhận. File sửa đổi hoặc nguồn mới cần xác minh manifest ở Backend trước.
- Preview chỉ lưu job và diagnostics. Xác nhận tạo mục mới ở `PENDING_REVIEW`, bỏ qua từ/từ loại đã có, không ghi đè nội dung Admin/Notebook/Review. Admin sửa và xuất bản từng mục qua Vocabulary CRUD hiện có. Không tự gán CEFR hoặc dịch cho nghĩa chưa đối chiếu được.
- Learner tra cứu qua `GET /api/vocabulary/search` (`keyword`, `partOfSpeech`, `cefrLevel`, `topicId`, `exact`, `page`, `size`; tối đa 100 mục/trang). `/filters` và `/{id}/links` chỉ dùng nội dung đã xuất bản. Catalog/Detail hiển thị nguồn, trang gốc, lịch sử người đóng góp và giấy phép.
- Chỉ thử import vào H2 hoặc `hola_features_test`; chưa có thao tác tự nạp pilot vào DB ứng dụng. Quy mô 2.000–3.000 mục cần kiểm duyệt nguồn/nội dung và đo hiệu năng riêng trước khi mở rộng.

- [LEARNER_FEATURES.md](LEARNER_FEATURES.md): API học viên và kiểm thử.
- [COURSE_IMPORT.md](COURSE_IMPORT.md): import Excel dành cho ADMIN.
- [MYSQL_DOCKER.md](MYSQL_DOCKER.md): cấu hình MySQL Docker và kiểm thử trên MySQL.
