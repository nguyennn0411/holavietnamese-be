# Notifications

Hệ thống lưu thông báo trong MySQL và đẩy thay đổi theo thời gian thực bằng SSE. REST API là nguồn dữ liệu chuẩn; sau khi SSE kết nối hoặc kết nối lại, frontend nên gọi lại API danh sách và số chưa đọc để đồng bộ.

## API

| Method | Endpoint | Mục đích |
|---|---|---|
| `GET` | `/api/notifications?page=1&size=10&isRead=false` | Danh sách phân trang; `isRead` là tùy chọn |
| `GET` | `/api/notifications/unread-count` | Số thông báo chưa đọc |
| `GET` | `/api/notifications/all` | API tương thích trả danh sách phẳng |
| `PUT` | `/api/notifications/{id}/read` | Đánh dấu một thông báo đã đọc |
| `PUT` | `/api/notifications/read-all` | Đánh dấu tất cả đã đọc |
| `DELETE` | `/api/notifications/{id}` | Xóa thông báo thuộc người dùng hiện tại |
| `GET` | `/api/notifications/stream` | Kết nối SSE |
| `POST` | `/api/notifications/broadcast` | Admin gửi thông báo hệ thống tới người dùng đang hoạt động |

`POST /api/notifications/test` chỉ hoạt động khi `app.notification.test-endpoint-enabled=true` và mặc định bị tắt ở production.

SSE ưu tiên Bearer header (dùng fetch-event-source hoặc client tương đương). Query `?token=...` vẫn được hỗ trợ cho native `EventSource`, nhưng token trong URL có thể xuất hiện trong log nên không phải lựa chọn ưu tiên.

## SSE events

| Event | Payload |
|---|---|
| `connected` | Xác nhận kết nối |
| `notification.created` | Toàn bộ `NotificationResponse` mới |
| `notification.read` | `notificationId`, `unreadCount` |
| `notification.read-all` | `unreadCount: 0` |
| `notification.deleted` | `notificationId`, `unreadCount` |

SSE chỉ được phát sau khi transaction lưu dữ liệu đã commit, nên client có thể đọc lại bản ghi ngay khi nhận event.

## Luồng nghiệp vụ đã kết nối

- **Xác thực & Bảo mật (`SECURITY`, `WELCOME`):**
  - Xác minh email / đăng nhập Google lần đầu / hoàn tất onboarding: thông báo chào mừng và bắt đầu hành trình học.
  - Đổi mật khẩu, đặt lại mật khẩu thành công: thông báo bảo mật gửi tức thì.
  - Admin thay đổi trạng thái tài khoản hoặc phân quyền vai trò: thông báo bảo mật bắt buộc.
- **Học tập & Khóa học (`COURSE`, `LESSON`, `ACHIEVEMENT`):**
  - Đăng ký khóa học mới: thông báo xác nhận và link vào học.
  - Hoàn thành bài học, vượt qua bài kiểm tra (Quiz) hoặc đạt điểm tuyệt đối: thông báo chúc mừng kèm điểm và XP.
  - Hoàn thành toàn bộ khóa học: thông báo vinh danh hoàn thành khóa học.
  - Khi một thao tác đồng thời kích hoạt hoàn thành quiz, bài học và khóa học, dispatcher ưu tiên giữ sự kiện cấp cao nhất để tránh spam.
- **Gamification & Thành tích (`STREAK`, `ACHIEVEMENT`):**
  - Chuỗi học tập liên tục (Daily Streak): thông báo ghi nhận streak ngày đầu và các mốc quan trọng (3, 7, 14, 30, 50, 100 ngày).
  - Mở khóa huy hiệu (Badges): tự động kiểm tra điều kiện (bài học đầu tiên, đạt điểm tuyệt đối, mốc streak, số từ vựng) và thông báo mở khóa huy hiệu kèm thưởng XP.
- **Sổ tay từ vựng (`SYSTEM`, `ACHIEVEMENT`):**
  - Thêm từ vựng vào sổ tay cá nhân và tích lũy số lượng từ vựng học được để mở khóa huy hiệu từ vựng tương ứng.
- **Lộ trình học cá nhân hóa (`COURSE`):**
  - Cập nhật mục tiêu và sở thích học tập: thông báo xác nhận cấu hình lộ trình.
  - Áp dụng lộ trình học thành công: thông báo danh sách khóa học được đề xuất và kích hoạt khóa học đầu tiên.
- **Gia sư & Kịch bản thực hành AI Roleplay (`SYSTEM`):**
  - Hoàn thành buổi hội thoại / tình huống giao tiếp với AI: thông báo tổng kết điểm số và cung cấp đường dẫn xem chi tiết đánh giá phản hồi.
- **Kiểm duyệt & Quản trị hệ thống (`COURSE`, `SYSTEM`):**
  - Giảng viên/Content Manager nộp khóa học: thông báo yêu cầu kiểm duyệt tới Admin.
  - Admin duyệt hoặc từ chối khóa học: thông báo kết quả kiểm duyệt kèm nhận xét tới người tạo khóa học.
  - Admin gửi thông báo toàn hệ thống (Broadcast): gửi tin tức tới tất cả người dùng đang hoạt động (tôn trọng cài đặt nhận thông báo của người dùng).

## Cấu hình

Các biến tương ứng nằm dưới `app.notification` trong `application.yml`:

- `NOTIFICATION_ENABLED`, `NOTIFICATION_REALTIME_ENABLED`
- `NOTIFICATION_TEST_ENDPOINT_ENABLED`
- `NOTIFICATION_MAX_PAGE_SIZE`, `NOTIFICATION_MAX_CONNECTIONS_PER_USER`
- `NOTIFICATION_STREAM_TIMEOUT`, `NOTIFICATION_HEARTBEAT_INTERVAL`
- `NOTIFICATION_RETENTION_DAYS`, `NOTIFICATION_CLEANUP_CRON`
- `NOTIFICATION_EVENT_POLL_INTERVAL`, `NOTIFICATION_EVENT_BATCH_SIZE`

Thông báo học tập/thông báo thường tôn trọng `users.notifications_enabled`. Thông báo `SECURITY` và workflow bắt buộc vẫn được lưu để người dùng không bỏ lỡ thay đổi quan trọng.

## Migration

`V36__notification_integration.sql` thêm khóa chống trùng và các index truy vấn. Vì cấu hình production hiện để Flyway tắt mặc định, cần áp dụng migration bằng quy trình triển khai hiện có hoặc bật Flyway sau khi đã kiểm tra lịch sử schema.
