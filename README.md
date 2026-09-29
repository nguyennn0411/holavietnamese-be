# Hola Vietnamese Backend

Backend Spring Boot được tổ chức theo monolith phân lớp. Frontend vẫn là dự án riêng; API và database không đổi.

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
Transaction, phân quyền, URL API, schema và migration được giữ nguyên.

## Chạy và kiểm thử

Yêu cầu Java 21 và Maven. Cache dependency dùng mặc định `%USERPROFILE%\.m2\repository`.

- Mở Docker Desktop và chạy `.\start-sqlserver.ps1` trong thư mục `BE`.
- Chạy `BackendApplication` trong IntelliJ với working directory là `BE`, hoặc `mvn spring-boot:run`.
- Profile mặc định `sqlserver` đọc mật khẩu từ `.env.sqlserver`; không commit file này.
- Sau lần chuyển cấu trúc này, chạy `mvn clean test` để bỏ class package cũ trong `target`.
- Test mặc định dùng H2 riêng, không thay đổi SQL Server đang chạy.

## Tài liệu

- [LEARNER_FEATURES.md](LEARNER_FEATURES.md): API học viên và kiểm thử.
- [COURSE_IMPORT.md](COURSE_IMPORT.md): import Excel dành cho ADMIN.
- [SQLSERVER_DOCKER.md](SQLSERVER_DOCKER.md): cấu hình SQL Server Docker.
