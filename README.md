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

## Tài liệu

- [LEARNER_FEATURES.md](LEARNER_FEATURES.md): API học viên và kiểm thử.
- [COURSE_IMPORT.md](COURSE_IMPORT.md): import Excel dành cho ADMIN.
- [MYSQL_DOCKER.md](MYSQL_DOCKER.md): cấu hình MySQL Docker và kiểm thử trên MySQL.
