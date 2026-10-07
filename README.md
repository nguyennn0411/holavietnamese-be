# Hola Vietnamese Backend

Backend Spring Boot được tổ chức theo monolith phân lớp, sử dụng MySQL 8.4 và Flyway. Frontend là ứng dụng React riêng trong `FE/holavietnamese_fe`.

Module course/activity/grammar/question bank/quiz mới nằm trong package `learning`.
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

## Tích hợp nhánh Huy (03/10/2026)

- Đã cập nhật `origin/main` tại `f1d9c23` (đã merge `feature-huy`).
- Đăng nhập/đăng ký JWT của Huy và Course/Quiz dùng chung bảng `users`, ID BIGINT. Migration V5 thêm profile và bảng role, giữ nguyên ID, enrollment, progress và quiz attempt hiện có.
- FE dùng `/api/auth/token`, `/api/auth/register`, `/api/users/me`; Bearer token được nhận ở toàn bộ API học tập. Session cũ vẫn hỗ trợ CSRF.
- `GET /api/users/progress` nối dashboard Huy với khóa học, từ vựng và lịch sử quiz thật. XP/streak/thời gian học chưa đo được trả về null.
- Chỉ dùng `compose.yml` (MySQL 8.4, volume hiện có); cấu hình compose MySQL 8.0 trùng của Huy đã được hợp nhất. Không chạy `down -v` để cập nhật code.
- `JWT_SECRET_KEY` và `GOOGLE_CLIENT_ID` được lấy từ môi trường. Google login cần client ID thật ở cả FE và BE. Đặt signing key riêng khi triển khai.
- Tài khoản demo chỉ được tạo khi bật profile `dev-demo`. Dùng `./start-demo.ps1` cho môi trường demo.
- Kiểm tra tích hợp JWT bằng `HuyAuthenticationIntegrationTests`; chạy cả `mvn test` và `./test-mysql.ps1`.

## Đồng bộ origin/main (07/10/2026)

- Giữ nguyên lịch sử Flyway V1–V9 của nhánh học tập. Master Vocabulary và Role Permissions từ main được nối tiếp bằng V10 và V11; các cột profile đã có từ V5 nên không tạo lại.
- Script chuẩn hóa ID cho database cũ từ main được lưu tại `src/main/resources/db/manual/normalize_legacy_user_schema.sql`, không chạy tự động: schema học tập đã dùng BIGINT và vẫn cần `enabled`/`role` cùng các khóa ngoại học tập.
- Cấu hình JWT, Google và email được hợp nhất trong `application.yml`; Flyway tiếp tục bật và Hibernate dùng `validate`.

## Tài liệu

- [LEARNER_FEATURES.md](LEARNER_FEATURES.md): API học viên và kiểm thử.
- [COURSE_IMPORT.md](COURSE_IMPORT.md): import Excel dành cho ADMIN.
- [MYSQL_DOCKER.md](MYSQL_DOCKER.md): cấu hình MySQL Docker và kiểm thử trên MySQL.
