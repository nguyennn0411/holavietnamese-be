# Hola Vietnamese Backend

## Tổng quan

Hola Vietnamese Backend là REST API được xây dựng bằng Spring Boot, phục vụ nền tảng học tiếng Việt Hola Vietnamese. Hệ thống sử dụng Java 21, MySQL 8.4 và Flyway để quản lý phiên bản cơ sở dữ liệu.

Backend được tổ chức theo mô hình modular monolith và package-by-feature. Mỗi module chịu trách nhiệm cho một nhóm nghiệp vụ độc lập, giúp source code dễ mở rộng, kiểm thử và bảo trì. MySQL chạy bằng Docker ở môi trường local và TiDB Cloud khi triển khai đều sử dụng chung một chuỗi migration tại `src/main/resources/db/migration`.

Frontend React được phát triển trong repository riêng `holavietnamese_fe`.

## Kiến trúc

```text
holavietnamese-be/
├── deployment/                         # Dockerfile và Docker Compose
├── scripts/
│   └── dev/                            # Script hỗ trợ môi trường local
├── src/
│   ├── main/
│   │   ├── java/com/sep490/backend/
│   │   │   ├── BackendApplication.java
│   │   │   ├── common/                 # Thành phần dùng chung
│   │   │   └── modules/
│   │   │       ├── admin/              # Quản trị, CMS và media
│   │   │       ├── ai/                 # AI tutor, roleplay và provider
│   │   │       ├── auth/               # Xác thực và phân quyền
│   │   │       ├── explore/            # Điểm đến và nội dung vùng miền
│   │   │       ├── learning/
│   │   │       │   ├── authoring/      # Biên soạn và duyệt nội dung
│   │   │       │   ├── course/         # Khóa học và đăng ký học
│   │   │       │   ├── grammar/        # Ngữ pháp
│   │   │       │   ├── importing/      # Import nội dung
│   │   │       │   ├── lesson/         # Bài học và hoạt động
│   │   │       │   ├── path/           # Lộ trình học tập
│   │   │       │   ├── progress/       # Tiến độ và thành tích
│   │   │       │   ├── quiz/           # Câu hỏi và bài kiểm tra
│   │   │       │   └── shared/         # Thành phần nội bộ dùng chung
│   │   │       └── vocabulary/         # Từ điển, sổ tay và ôn tập
│   │   └── resources/
│   │       ├── application.yml         # Cấu hình Spring Boot
│   │       └── db/migration/            # Flyway migration V1–V48
│   └── test/                            # Unit test và integration test
├── .env.example                        # Mẫu biến môi trường
├── pom.xml                              # Maven dependencies
└── README.md
```

Các module sở hữu controller, application service, domain và persistence của mình. Thành phần trong `common` chỉ chứa hợp đồng và hạ tầng thực sự dùng chung; module nghiệp vụ không được tạo phụ thuộc vòng.

Flyway là nguồn duy nhất quản lý schema. MySQL local và TiDB Cloud không sử dụng các thư mục migration riêng biệt.

## Cấu hình `.env`

Sao chép file mẫu trước khi chạy dự án:

```powershell
Copy-Item .env.example .env
```

Không commit file `.env` hoặc credential thật lên Git. Mỗi biến phải nằm trên một dòng theo định dạng `KEY=value`; không thêm dấu `\` cuối dòng và không escape `://`.

Ba biến quyết định database mà backend kết nối:

```env
DB_URL=jdbc:mysql://127.0.0.1:3307/hola_vietnamese?connectionTimeZone=UTC&allowPublicKeyRetrieval=true&useSSL=false
DB_USERNAME=hola
DB_PASSWORD=change-me-local-app-password
```

Các biến dành cho MySQL Docker local:

```env
MYSQL_DATABASE=hola_vietnamese
MYSQL_USER=hola
MYSQL_PASSWORD=change-me-local-app-password
MYSQL_ROOT_PASSWORD=change-me-different-root-password
MYSQL_PORT=3307
```

Khi backend kết nối MySQL Docker local, `DB_PASSWORD` phải giống `MYSQL_PASSWORD`. Các dịch vụ tùy chọn như email, Cloudinary, Google Login và AI được cấu hình trong cùng file `.env`; xem danh sách đầy đủ tại `.env.example`.

## Chạy với MySQL local hoặc TiDB Cloud

### Yêu cầu

- Java 21.
- Maven 3.9 trở lên.
- Docker Desktop sử dụng Linux containers.

### MySQL Docker local

Khởi động riêng MySQL:

```powershell
.\scripts\dev\start-mysql.ps1
```

Sau đó chạy backend trong IntelliJ bằng `BackendApplication`, hoặc dùng Maven:

```powershell
mvn spring-boot:run
```

Để chạy cả MySQL và backend bằng Docker:

```powershell
.\scripts\dev\start-mysql.ps1 -StartBackend
```

Thông tin mặc định:

```text
MySQL:   127.0.0.1:3307
Backend: http://localhost:8080
Database: hola_vietnamese
```

Script sẽ tự tạo `.env` từ `.env.example` và sinh credential local ngẫu nhiên nếu file `.env` chưa tồn tại.

### TiDB Cloud

Giữ nguyên source code và Flyway migration. Chỉ thay ba biến kết nối backend trong `.env`:

```env
DB_URL=jdbc:mysql://<tidb-host>:4000/hola_vietnamese?sslMode=VERIFY_IDENTITY&enabledTLSProtocols=TLSv1.2,TLSv1.3&connectionTimeZone=UTC
DB_USERNAME=<tidb-username>
DB_PASSWORD=<tidb-password>
```

Sau đó khởi động lại backend:

```powershell
mvn spring-boot:run
```

Các biến `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` và `MYSQL_PORT` chỉ dùng để tạo MySQL Docker local; chúng không cấu hình kết nối TiDB của backend.

Với database mới, giữ cấu hình:

```env
FLYWAY_ENABLED=true
FLYWAY_VALIDATE_ON_MIGRATE=true
```

Không trỏ phiên bản code đã đổi tên migration vào database cũ có `flyway_schema_history` không tương thích. Hãy sử dụng database mới hoặc kiểm tra và đồng bộ lịch sử Flyway trước khi triển khai.
