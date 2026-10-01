# MySQL + Spring Boot trên Docker

Yêu cầu Docker Desktop với Linux containers; script dùng PowerShell 7.

```powershell
cd D:\DO_AN\BE
.\start-mysql.ps1 -StartBackend
```

Lần đầu script tạo `.env.mysql` với hai mật khẩu ngẫu nhiên riêng biệt.
Có thể copy `.env.mysql.example` thành `.env.mysql` và sửa trước khi chạy.
File này được Git bỏ qua và không được copy vào image.

- MySQL 8.4: `localhost:3307`, database `hola_vietnamese`, user `hola`.
- BE: `http://localhost:8080/api/courses`.
- Trong Docker, BE kết nối `mysql:3306`; chạy trên máy thì dùng `localhost:3307`.
- Volume `mysql-data` lưu dữ liệu qua các lần restart. Không dùng `down -v` nếu muốn giữ dữ liệu.
- UTF-8 `utf8mb4`, collation phân biệt dấu và không phân biệt hoa thường; thời gian UTC.
- Flyway tự chạy V1–V3, Hibernate kiểm tra schema khi BE khởi động.

```powershell
docker compose --env-file .env.mysql up -d --build --wait
docker compose --env-file .env.mysql ps
docker compose --env-file .env.mysql logs --tail 100 backend
docker compose --env-file .env.mysql stop
```

Chạy BE trong IDE: dùng `.\start-mysql.ps1` chỉ để bật MySQL, rồi chạy
`mvn spring-boot:run` từ `BE`. Không chọn profile `sqlserver` cũ.
Nếu đã chạy BE trong Docker, dừng service `backend` trước khi chạy IDE để tránh trùng cổng.
Nếu đổi `BACKEND_PORT`, sửa `VITE_PROXY_TARGET` trong `.env.local` của FE tương ứng.
Trên máy hiện tại, `.env.mysql` dùng `BACKEND_PORT=8088` do cổng 8080 đã được dự án khác sử dụng;
API local là `http://localhost:8088/api/courses`.

Mật khẩu và database trong `.env.mysql` chỉ khởi tạo volume ở lần chạy đầu.
Thay đổi file sau đó không tự đổi mật khẩu user trong database đã tồn tại.

## Kiểm thử

`mvn clean test` dùng H2 cô lập. Để kiểm thử MySQL thực:

```powershell
.\test-mysql.ps1
```

Script tạo database riêng `hola_features_test`, cấp quyền cho user MySQL của ứng dụng,
rồi chạy Maven với profile `mysql-test`. Cần Java 21 và Maven trên PATH
(hoặc truyền `-MavenCommand` là đường dẫn tới `mvn.cmd`). Test chỉ xóa fixture trong database test.

## Dữ liệu cũ

Flyway hiện tại có cảnh báo chưa kiểm chứng MySQL 8.4 chính thức. Ba migration và bộ test
tích hợp đã chạy thành công trên MySQL 8.4; cảnh báo này vẫn còn trong log.

Đây là chuyển cấu hình và schema sang MySQL, không phải ETL dữ liệu SQL Server.
SQL Server volume và `.env.sqlserver` cũ được giữ nguyên. Các file cấu trúc cũ được
lưu tại `../outputs/migration-backup-20261001` để có thể khôi phục.
