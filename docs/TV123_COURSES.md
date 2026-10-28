# Tiếng Việt 123 - lộ trình course theo cấp độ

Nguồn nội dung là file `734298750-tiengviet123.pdf`. Hình minh họa được trích từ các trang mở đầu bài học. Audio dùng bản công khai của 123Vietnamese tại:

`https://123vietnamese.com/audio/tieng-viet-123-student-book-2018/`

## Cấu trúc

| Code | Cấp độ | Phạm vi | Số bài |
| --- | --- | --- | ---: |
| `TV123-A0` | A0 | Bảng chữ cái, nguyên âm, phụ âm, thanh điệu | 4 |
| `TV123-A1-1` | A1 | Bài 1-5: giới thiệu, nghề nghiệp, tuổi, nhà ở, mua sắm | 5 |
| `TV123-A1-2` | A1 | Bài 6-10: thời gian, ngày tháng, trải nghiệm, thói quen, chỉ đường | 5 |
| `TV123-A2` | A2 | Bài 11-16: khả năng, vị trí, gọi món, thời tiết, khách sạn, sức khỏe | 6 |

Mỗi bài có hình từ giáo trình, audio giọng Bắc và giọng Nam, từ vựng, hội thoại, ngữ pháp, quiz cuối bài. Mỗi course có checkpoint và đánh giá cuối khóa. Việc chia A0/A1.1/A1.2/A2 là cách tổ chức sư phạm của nền tảng; giáo trình gốc ghi chung là Trình độ A.

## Tạo dữ liệu trong MySQL Docker

```powershell
docker compose --env-file .env.mysql -f deployment/compose.yml -f deployment/compose.demo.yml -f deployment/compose.tv123.yml up -d --build --wait
```

Seed có tính lặp an toàn: nếu course code đã tồn tại, lần chạy sau không tạo bản trùng.

## Tạo lại JSON sau khi chỉnh nội dung

```powershell
python scripts/author-tv123-courses.py
```
