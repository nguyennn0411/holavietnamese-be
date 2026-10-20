# Code và dữ liệu nội dung trên nhánh backup

Dữ liệu đi cùng source gồm JSON biên soạn khóa học/bài học/quiz, seed Java, migration MySQL và ảnh/âm thanh trong `src/main/resources/static/media`. Không cần sao chép ID hay import bản dump database cá nhân.

## Nạp bộ demo trên máy khác

1. Pull nhánh `backup` của cả BE và FE.
2. Tại BE, bật Docker Desktop rồi chạy `./start-mysql.ps1` bằng PowerShell 7. Script tạo `.env.mysql` từ file mẫu nếu chưa có, với mật khẩu MySQL và khóa JWT riêng cho máy.
3. Nếu dùng database đã có dữ liệu, đối chiếu lịch sử Flyway theo [INTEGRATION_DEMO.md](INTEGRATION_DEMO.md) trước khi khởi động backend. Không dùng Repair để bỏ qua checksum khác hoặc đổi tên file đã chạy trên database đó.
4. Nạp các bộ nội dung:

```powershell
docker compose --env-file .env.mysql -f compose.yml -f compose.demo.yml -f compose.content-demo.yml up -d --build --wait
```

5. Tại FE, chạy `npm ci` rồi `npm run dev` và mở `http://localhost:5173`.
6. Tại BE, chạy `node scripts/verify-integration-demo.cjs` để lấy ID thực tế và xác nhận liên kết.

Bộ nạp bật các course A1, nền tảng, phát âm, chương 1–3, TV123, gói TV123 LMS và khóa demo liên kết Hà Nội → Hội An → TP.HCM. Seed dùng lại course đã tồn tại; nếu cấu trúc course trùng mã nhưng khác dữ liệu biên soạn, seed dừng để người phụ trách kiểm tra, không ghi đè tiến độ học.

Khóa liên kết có mã `DEMO-VIETNAM-LINKS`, gồm 6 bài, 18 từ và 7 quiz. Xem [kịch bản tích hợp](INTEGRATION_DEMO.md), [TV123](TV123_COURSES.md) và [gói LMS](TV123_PACKAGE_COURSES.md).

Đã kiểm chứng lệnh bật toàn bộ seed trên MySQL tạm với V1–V26: nạp thành công **16 khóa đã xuất bản, 290 bài học, 18 từ dùng chung và 3 chặng hành trình**. ID giữa các máy có thể khác nhau. Các bản nháp tạo thử riêng trên database của người biên soạn không nằm trong bộ demo này.

Các tài khoản mẫu chỉ được tạo với profile `dev-demo`: `admin / admin123`, `contentmanager / contentmanager123`, `learner / learner123`. Khóa Cloudinary, SMTP, AI và cấu hình riêng phải đặt trong `.env.mysql` hoặc `.env.cloudinary` cục bộ; file mẫu để trống các khóa dịch vụ.

## Phạm vi dữ liệu được chia sẻ

- Nội dung biên soạn, media và mã nạp dữ liệu nằm trong Git.
- Tài khoản thực tế, hash mật khẩu, token, tiến độ học, nhật ký và khóa dịch vụ của database cục bộ không nằm trong gói này.
- Mỗi máy tự sinh ID khi nạp. Liên kết bài học/từ/địa điểm được tạo từ ID vừa trả về; không dùng ID cố định của máy biên soạn.
- MySQL vẫn lưu dữ liệu đang chạy trong Docker volume của từng máy; `git pull` không tự đồng bộ database đó. Phải chạy lệnh seed phía trên để có nội dung mẫu tương ứng.
