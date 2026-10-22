# Khóa học Tiếng Việt 1 – Tập 1: Học vần

## Nội dung đã xuất bản

- Mã khóa học: `VI1-TAP1-TEXTBOOK`; ID tại database demo hiện tại: `20`.
- Trang học viên: http://localhost:5173/courses/20
- Trình biên soạn: http://localhost:5173/admin/courses/20?step=curriculum
- 12 chương, 83 bài học, 753 hoạt động, 409 câu hỏi riêng biệt.
- 83 quiz cuối bài và một quiz tổng kết 24 câu lấy từ ngân hàng câu hỏi của khóa.
- 173 trang gốc và 384 vùng tranh/chữ cắt bổ sung, tổng cộng 557 ảnh trên Cloudinary.
- 81 đoạn đọc ứng dụng được nhập thành văn bản, thẻ từ khóa, luyện nói/kể chuyện và hướng dẫn tập viết.

Nguồn: `Tiếng Việt 1 tập 1.pdf`, 173 trang. SHA-256:
`af2bb8a065b21aeae6c2941e989f9942c0150ad3e578f1f00d052ca4c97e8596`.
PDF nguồn được giữ nguyên. Các trang đầy đủ giữ cả bảng chữ, mẫu viết và những tranh đen trắng hoặc bố cục phức tạp; ảnh cắt là phần bổ sung, không thay thế trang gốc. Hướng dẫn, chú giải tiếng Anh và quiz số hóa là nội dung biên soạn thêm. Tập viết và phát âm là hoạt động tự luyện, không có chấm tự động.

## Tệp dữ liệu

Trong `src/main/resources/learning/`:

- `tv1-tap1-assets.json`: trang nguồn, tọa độ vùng cắt và mã băm.
- `tv1-tap1-media.json`: ánh xạ ảnh cục bộ sang URL Cloudinary.
- `tv1-tap1-readings.json`: đoạn đọc và chủ đề luyện nói/kể chuyện.
- `tv1-tap1-course.json`: toàn bộ cấu trúc khóa học và ngân hàng câu hỏi.

Ảnh gốc và ảnh cắt nằm trong `src/main/resources/static/media/tv1-tap1/`.

## Nạp lại trên môi trường khác

Chạy từ thư mục BE, backend đang hoạt động. Import dùng API Content Manager và Admin, không sửa bảng tiến độ, migration hoặc lịch sử Flyway. Mã khóa học và mã bài học giúp tiếp tục lần nhập bị gián đoạn. Khóa đã xuất bản được giữ nguyên khi chạy lại.

```powershell
node scripts/import-tv1-textbook.cjs
node scripts/import-tv1-textbook.cjs --apply
```

Sau khi duyệt nội dung và được cho phép xuất bản:

```powershell
node scripts/import-tv1-textbook.cjs --apply --publish
```

Cấu hình tùy chọn: `DEMO_API_URL`, `DEMO_MANAGER_USERNAME`, `DEMO_MANAGER_PASSWORD`, `DEMO_ADMIN_USERNAME`, `DEMO_ADMIN_PASSWORD`, `TV1_OUTPUT_DIR`. Giá trị tài khoản mặc định chỉ dành cho môi trường demo cục bộ.

Các URL ảnh đã lưu có thể dùng lại, không cần tải lại. Nếu cần tải lên tài khoản Cloudinary khác, phải có quyền chia sẻ ảnh với dịch vụ đó; cấu hình bí mật trong `.env.cloudinary` bị Git bỏ qua rồi chạy:

```powershell
node --env-file=.env.cloudinary scripts/upload-tv1-textbook.cjs
```

Không đưa API secret, token hoặc tệp cấu hình bí mật lên Git.

## Kiểm tra

```powershell
node scripts/verify-tv1-textbook.cjs
# Bỏ kiểm tra HEAD của 557 URL khi các URL vừa được kiểm tra thành công:
node scripts/verify-tv1-textbook.cjs --skip-image-head
```

Script kiểm tra chỉ đọc nội dung, không ghi danh hoặc tạo tiến độ/lượt làm bài. Kiểm tra đủ 83 bài, 173 trang, 557 ảnh, câu hỏi/đáp án, liên kết quiz, trạng thái xuất bản và readiness. Khi khóa đã xuất bản, script kiểm tra thêm API công khai dành cho học viên.

Báo cáo lần xuất bản hiện tại: `D:\DO_AN\outputs\textbook-tv1-20261020\verification.json`.
