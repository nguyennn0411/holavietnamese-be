# Hola Vietnamese Backend

Backend Spring Boot được tổ chức theo modular monolith/package-by-feature, sử dụng MySQL 8.4 và Flyway. Frontend là ứng dụng React riêng trong `FE/holavietnamese_fe`.

Các chức năng course, lesson, grammar, quiz, progress và import nằm trong các capability riêng của module `learning`.
Các lesson đã xuất bản được học theo thứ tự tùy ý sau khi đăng ký course; bài tiên quyết không còn khóa truy cập. Điều kiện hoàn thành activity, quiz và course vẫn được kiểm tra khi ghi nhận tiến độ.

## Cấu trúc

```text
BE/
├── deployment/         # Dockerfile và Docker Compose cho ứng dụng
├── docs/               # Tài liệu kiến trúc và hướng dẫn tính năng
├── scripts/
│   ├── dev/            # Script khởi động, dừng và kiểm thử môi trường local
│   └── ...             # Import MSU và tiện ích media
├── src/                 # Source code và test của Spring Boot
├── pom.xml
└── README.md

src/main/java/com/sep490/backend/
├── BackendApplication.java
├── common/              # Hợp đồng và hạ tầng dùng chung
└── modules/
    ├── admin/           # Quản trị hệ thống, CMS và media
    ├── ai/              # AI tutor, roleplay và provider
    ├── auth/            # Xác thực, người dùng và phân quyền
    ├── explore/         # Điểm đến và ngôn ngữ vùng miền
    ├── learning/
    │   ├── authoring/   # Content Manager và workflow duyệt nội dung
    │   ├── course/      # Khóa học và đăng ký học
    │   ├── grammar/     # Nội dung ngữ pháp
    │   ├── importing/   # Import Excel và website
    │   ├── lesson/      # Bài học và activity
    │   ├── path/        # Lộ trình cá nhân hóa
    │   ├── progress/    # Tiến độ, XP và thành tích
    │   ├── quiz/        # Question bank, quiz và attempt
    │   └── shared/      # Thành phần dùng chung nội bộ learning
    └── vocabulary/      # Từ điển, sổ tay và ôn tập
```

Mỗi module sở hữu controller, application service, domain và persistence của mình. Các port hẹp được dùng khi một module cần dữ liệu từ module khác.
Transaction, phân quyền và URL API được giữ nguyên. Flyway dùng migration MySQL trong `src/main/resources/db/migration`.

## Chạy và kiểm thử

Yêu cầu Java 21 và Maven. Cache dependency dùng mặc định `%USERPROFILE%\.m2\repository`.

- Mở Docker Desktop (Linux containers), chạy `.\scripts\dev\start-mysql.ps1 -StartBackend` bằng PowerShell 7 để chạy MySQL và BE trong Docker.
- Script tự tạo `.env.mysql` với mật khẩu ngẫu nhiên nếu chưa có. Không commit file này.
- Nếu chạy BE trong IntelliJ: chạy `.\scripts\dev\start-mysql.ps1` để bật MySQL, rồi chạy `BackendApplication` với working directory là `BE`, hoặc `mvn spring-boot:run`.
- MySQL ở `localhost:3307`. BE Docker ở `http://localhost:18080` (biến `DOCKER_BACKEND_PORT`); chạy BE trực tiếp từ IntelliJ dùng `BACKEND_PORT` (máy hiện tại: 8088).
- Chạy `mvn clean test` với Java 21. Test mặc định dùng H2 riêng.
- MySQL dùng volume mới; dữ liệu SQL Server cũ chưa được chuyển tự động.

## Tài liệu

- [MSU_OPENBOOKS_CONTENT.md](MSU_OPENBOOKS_CONTENT.md): tái tạo 22 khóa học mẫu Basic/Advanced Vietnamese từ dữ liệu nguồn đã commit.
- [INTEGRATION_DEMO.md](docs/INTEGRATION_DEMO.md): luồng Khám phá Việt Nam hiện tại và lưu ý đối chiếu lịch sử migration.

### Từ vựng song ngữ và Excel

- Learner tra cứu qua `GET /api/vocabulary/search` với `keyword`, `page`, `size`. Tìm tên tiếng Việt hoặc thông tin nghĩa tiếng Anh đã lưu; không tự dịch. Chỉ hiển thị mục `PUBLISHED`.
- Admin tìm theo `keyword`, `status`, `partOfSpeech`. Không còn phân loại từ theo trình độ hoặc chủ đề. Các bảng/cột cũ được giữ trong DB và migration để bảo toàn lịch sử; ứng dụng không đọc/ghi chúng.
- Mẫu XLSX có sheet `VOCABULARY`. Header bắt buộc: `word`, `part_of_speech`. Mỗi nghĩa cần ít nhất một trường: `definition_vi`, `translation_en`, `definition_en`.
- Header đầy đủ: `word`, `part_of_speech`, `pronunciation`, `audio_url`, `status`, `meaning_order`, `definition_vi`, `translation_en`, `definition_en`, `usage_note`, `labels`, `example_order`, `example_vi`, `example_en`, `example_audio_url`.
- Ghi `word` và `part_of_speech` trên mỗi dòng. Các dòng cùng từ/từ loại được gom thành một mục. `meaning_order` (mặc định 0) phân biệt các nghĩa; `example_order` sắp xếp ví dụ, để trống thì nối tiếp. Các trường lặp có thể để trống; nếu giá trị khác nhau trong cùng mục/nghĩa, preview báo lỗi. `labels`: mỗi nhãn một dòng trong ô.
- Ví dụ cần `example_vi`; bản dịch và audio ví dụ là tùy chọn. Không tự tạo bản dịch còn thiếu. Trạng thái trống là `DRAFT`.
- Preview không ghi mục từ. Confirm kiểm tra lại và nhập toàn bộ trong một giao dịch; từ/từ loại đã tồn tại bị từ chối, không ghi đè. `importedRows` đếm số mục từ tạo mới, có thể ít hơn số dòng Excel.
- Nội dung, thông tin nguồn lịch sử và liên kết của các mục từ đã có vẫn được giữ. Không còn phương thức import JSONL hoặc dữ liệu pilot kèm source.

- [LEARNER_FEATURES.md](docs/LEARNER_FEATURES.md): API học viên và kiểm thử.
- [COURSE_IMPORT.md](docs/COURSE_IMPORT.md): import Excel dành cho ADMIN.
- [MYSQL_DOCKER.md](docs/MYSQL_DOCKER.md): cấu hình MySQL Docker và kiểm thử trên MySQL.
