# Bộ course Tiếng Việt 123 từ gói LMS

Gói `Tieng_Viet_123_LMS_Courses.zip` được đưa vào `src/main/resources/learning/tv123-package-courses.json` và 19 ảnh tại `src/main/resources/static/media/tv123/package/`. Dữ liệu nạp thành bốn course `TV123-C00` đến `TV123-C03`, tách khỏi bốn course `TV123-A0` đến `TV123-A2` đã có.

## Nạp vào MySQL Docker

```powershell
docker compose --env-file .env.mysql -f deployment/compose.yml -f deployment/compose.demo.yml -f deployment/compose.tv123.yml -f deployment/compose.tv123-package.yml up -d --build --wait
```

Bộ nạp chỉ chạy khi `APP_TV123_PACKAGE_SEED_ENABLED=true`. Nó yêu cầu tài khoản ADMIN đã tồn tại, tạo course trong một giao dịch và không tạo lại nếu các mã course đã có đủ số lesson tương ứng. Nếu course có cùng mã nhưng cấu trúc khác, quá trình dừng để tránh ghi đè tiến độ học.

Gói có 4 course, 20 module, 88 lesson và 138 câu hỏi. 118 câu trắc nghiệm, điền từ và sắp xếp từ được nạp vào 20 quiz tự chấm. 20 câu tự luận được giữ thành hoạt động đọc để giáo viên đánh giá thủ công. Các hoạt động nghe chưa có bản ghi âm trong gói nên không được nạp thành hoạt động hoàn thành tự động.

Ảnh và một phần nội dung lấy từ giáo trình; cần xác nhận quyền sử dụng trước khi công bố rộng rãi. Các mức A0/A1/A2 trong LMS chỉ phục vụ tổ chức nội bộ, không phải chứng chỉ hay quy đổi trình độ chính thức.
