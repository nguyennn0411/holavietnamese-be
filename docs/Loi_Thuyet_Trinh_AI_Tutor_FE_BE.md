# Lời thuyết trình: Luồng AI Tutor từ FE sang BE

> [!TIP]
> Mỗi phần có hai dòng. **🖥️ Màn hình** là thứ bạn mở hoặc click. **🎤 Nói** là lời thoại. Tổng thời lượng khoảng **6–7 phút**.
> Trước khi trình bày, hãy mở sẵn ba tab: sequence diagram, project FE `holavietnamese_fe` và project BE `holavietnamese-be`.

---

## Mở đầu (~30 giây)

**🖥️ Màn hình:** Sequence diagram *"AI Tutor conversation with provider routing"*.

**🎤 Nói:**
> "Em xin trình bày luồng **AI Tutor**, tức chức năng học viên trò chuyện với gia sư AI. Em sẽ bám theo sequence diagram này và chỉ trực tiếp trên code: người dùng **bấm nút nào ở Frontend**, request **đi qua những lớp nào ở Backend**, và tại sao thiết kế như vậy.
>
> Diagram có 3 giai đoạn: **bắt đầu hội thoại**, **gửi câu hỏi** và **kết thúc phiên**. Các thành phần tham gia gồm giao diện React, `AiTutorController`, `AiTutorService`, `AiProviderService`, hai client gọi OpenAI hoặc Gemini, và cơ sở dữ liệu MySQL."

---

## Phần 0: FE kết nối với BE như thế nào (~45 giây)

**🖥️ Màn hình:** [routes.js#L67](file:///D:/holavietnamese_fe/src/constants/routes.js#L67) → [AppRouter.jsx#L180](file:///D:/holavietnamese_fe/src/app/router/AppRouter.jsx#L180)

**🎤 Nói:**
> "Ở Frontend, đường dẫn `/ai-tutor` được router gắn với component `AiTutorPage`. Đây là màn hình chat mà học viên nhìn thấy."

**🖥️ Màn hình:** [axiosClient.js#L3-L45](file:///D:/holavietnamese_fe/src/shared/api/axiosClient.js#L3-L45)

**🎤 Nói:**
> "Mọi request từ FE lên BE đều đi qua `axiosClient` dùng chung. File này làm hai việc:
> - Thứ nhất, đặt **base URL** trỏ tới Backend, lấy từ biến môi trường `VITE_API_BASE_URL`.
> - Thứ hai, **interceptor tự gắn JWT token** vào header `Authorization`. Nhờ vậy BE biết chính xác ai đang gọi mà FE không cần gửi `userId` lên. Điều này rất quan trọng cho phần bảo mật em sẽ nói ở sau.
>
> Nối giữa hai bên chính là **URL API**. Khi demo, em sẽ copy đường dẫn ở FE rồi tìm trong BE để thấy đúng endpoint nhận request."

---

## Phần 1: Bắt đầu hội thoại (~1 phút)

**🖥️ Màn hình:** [AiTutorPage.jsx#L742-L750](file:///D:/holavietnamese_fe/src/features/ai/pages/ai-tutor/AiTutorPage.jsx#L742-L750). Chỉ vào nút **"Cuộc trò chuyện mới"**.

**🎤 Nói:**
> "Trên diagram, bước đầu tiên là *User → Start a conversation*. Trên giao diện, đó là nút **Cuộc trò chuyện mới**. Khi bấm, nút này gọi hàm `handleCreateNewChat`."

**🖥️ Màn hình:** [`handleCreateNewChat`](file:///D:/holavietnamese_fe/src/features/ai/pages/ai-tutor/AiTutorPage.jsx#L379-L392) → [`aiTutorService.createSession`](file:///D:/holavietnamese_fe/src/features/ai/api/aiTutorService.js#L911-L956)

**🎤 Nói:**
> "Hàm này gọi lớp service phía FE là `createSession`. Ở đây FE tạo mã phiên, hiển thị lời chào ngay để giao diện phản hồi nhanh, đồng thời gửi **`POST /api/ai/sessions`** lên Backend. Đây chính là mũi tên *UI → AiTutorController* trên diagram."

**🖥️ Màn hình:** Chuyển sang BE: [`AiTutorController.createSession`](file:///D:/holavietnamese-be/src/main/java/com/sep490/backend/modules/ai/controller/AiTutorController.java#L52-L59)

**🎤 Nói:**
> "Sang Backend, request vào `AiTutorController`. Controller **không chứa logic nghiệp vụ**. Nó chỉ validate dữ liệu, chuyển cho service rồi bọc kết quả vào `ApiResponse` chuẩn."

**🖥️ Màn hình:** [`AiTutorService.createSession`](file:///D:/holavietnamese-be/src/main/java/com/sep490/backend/modules/ai/service/AiTutorService.java#L63-L118), dừng ở [L68-L73](file:///D:/holavietnamese-be/src/main/java/com/sep490/backend/modules/ai/service/AiTutorService.java#L68-L73)

**🎤 Nói:**
> "Trong `AiTutorService`, đúng như diagram ghi *Resolve current user, load settings, check daily quota*, service:
> 1. Lấy user hiện tại từ JWT,
> 2. Đọc cấu hình AI do Admin thiết lập,
> 3. **Kiểm tra hạn mức số phiên trong ngày**. Nếu vượt thì báo lỗi `AI_QUOTA_EXCEEDED`.
>
> Sau đó service lưu phiên với trạng thái **ACTIVE** cùng lời chào đầu tiên vào MySQL, rồi trả `AiSessionResponse` về FE."

---

## Phần 2: Gửi câu hỏi (phần trọng tâm, ~2,5 phút)

### 2.1 Phía Frontend

**🖥️ Màn hình:** [AiTutorPage.jsx#L1365](file:///D:/holavietnamese_fe/src/features/ai/pages/ai-tutor/AiTutorPage.jsx#L1365) và nút Send [L1401-L1413](file:///D:/holavietnamese_fe/src/features/ai/pages/ai-tutor/AiTutorPage.jsx#L1401-L1413)

**🎤 Nói:**
> "Bước tiếp theo trên diagram là *Ask a question*. Học viên có 3 cách gửi: bấm nút **Gửi**, nhấn **Enter**, hoặc bấm một **chip gợi ý**. Cả 3 cách đều đi về một hàm duy nhất là `handleSendMessage`."

**🖥️ Màn hình:** [`handleSendMessage`](file:///D:/holavietnamese_fe/src/features/ai/pages/ai-tutor/AiTutorPage.jsx#L437-L468) → [`sendMessage`](file:///D:/holavietnamese_fe/src/features/ai/api/aiTutorService.js#L1044-L1173) → [`requestAiResponse`](file:///D:/holavietnamese_fe/src/features/ai/api/aiTutorService.js#L455-L473)

**🎤 Nói:**
> "`handleSendMessage` khóa nút gửi để tránh bấm hai lần, rồi gọi `sendMessage` ở service FE. Ở dòng 1059, FE **chờ request tạo phiên hoàn tất** rồi mới gửi tin nhắn, để không bị tranh chấp thứ tự. Cuối cùng, `requestAiResponse` gửi **`POST /api/ai/sessions/{id}/messages`** với nội dung câu hỏi, chế độ học và ngôn ngữ."

### 2.2 Phía Backend

**🖥️ Màn hình:** [`AiTutorController.sendMessage`](file:///D:/holavietnamese-be/src/main/java/com/sep490/backend/modules/ai/controller/AiTutorController.java#L97-L107) → [`AiTutorService.sendMessage`](file:///D:/holavietnamese-be/src/main/java/com/sep490/backend/modules/ai/service/AiTutorService.java#L200-L285)

**🎤 Nói:**
> "Request vào Controller rồi sang `AiTutorService.sendMessage`. Hàm này là trái tim của luồng, em chia làm 5 bước."

**🖥️ Màn hình:** [L202-L217](file:///D:/holavietnamese-be/src/main/java/com/sep490/backend/modules/ai/service/AiTutorService.java#L202-L217) + [`resolveUserSessionId`](file:///D:/holavietnamese-be/src/main/java/com/sep490/backend/modules/ai/service/AiTutorService.java#L379-L396)

**🎤 Nói:**
> "**Bước 1: Cô lập dữ liệu theo người dùng.** Diagram có nhánh *Another user owns this session*. Trong code, phiên luôn được tìm bằng `findByIdAndUserId`, tức **mã phiên kèm id của user lấy từ JWT**. Nếu ai đó gửi mã phiên của người khác, hàm `resolveUserSessionId` sẽ gắn thêm hậu tố user hiện tại. Request đó sẽ rơi vào một phiên riêng của chính họ, **không bao giờ đọc hay ghi được hội thoại của người khác**."

**🖥️ Màn hình:** [L231-L253](file:///D:/holavietnamese-be/src/main/java/com/sep490/backend/modules/ai/service/AiTutorService.java#L231-L253)

**🎤 Nói:**
> "**Bước 2: Lưu tin nhắn và chuẩn bị ngữ cảnh.** Tin nhắn của học viên được lưu trước. Sau đó:
> - `IntentClassifier` xác định học viên đang hỏi gì: phát âm, từ vựng, dịch, sửa ngữ pháp hay trò chuyện,
> - hệ thống cập nhật **bản tóm tắt ngữ cảnh** để AI nhớ cuộc hội thoại mà không phải gửi lại toàn bộ lịch sử,
> - rồi tạo **system prompt cá nhân hóa** theo trình độ, ngôn ngữ mẹ đẻ và mục tiêu học của từng người."

**🖥️ Màn hình:** [L256-L257](file:///D:/holavietnamese-be/src/main/java/com/sep490/backend/modules/ai/service/AiTutorService.java#L256-L257) → [`AiProviderService`](file:///D:/holavietnamese-be/src/main/java/com/sep490/backend/modules/ai/service/AiProviderService.java#L18-L71)

**🎤 Nói:**
> "**Bước 3: Định tuyến nhà cung cấp AI.** Đây là phần *provider routing* trong tên diagram. Service không gọi thẳng OpenAI hay Gemini mà đi qua `AiProviderService`. Hàm `isOpenAiModel` xem tên model: bắt đầu bằng `gpt-`, `openrouter/`, `anthropic/` hoặc `meta-llama/` thì gọi `OpenAiApiClient`, còn lại thì gọi `GeminiApiClient`.
>
> Model được lấy từ **cấu hình của Admin**, không phải từ FE. Nhờ vậy Admin đổi nhà cung cấp mà **không phải sửa dòng code nghiệp vụ nào**. Đây là áp dụng **Strategy Pattern**."

**🖥️ Màn hình:** [L264-L272](file:///D:/holavietnamese-be/src/main/java/com/sep490/backend/modules/ai/service/AiTutorService.java#L264-L272)

**🎤 Nói:**
> "**Bước 4: Xử lý kết quả và cơ chế dự phòng.** Diagram có hai nhánh:
> - Nếu provider trả về nội dung hợp lệ, hệ thống phân tích và dựng tin nhắn AI.
> - Nếu **thiếu API key, mất mạng hoặc provider lỗi**, client trả về `null` thay vì ném lỗi. Service chuyển sang `generateOfflineTutorResponse` để tạo câu trả lời dựa trên intent đã phân loại. Học viên **vẫn nhận được phản hồi**, không gặp lỗi 500."

**🖥️ Màn hình:** [L274-L284](file:///D:/holavietnamese-be/src/main/java/com/sep490/backend/modules/ai/service/AiTutorService.java#L274-L284)

**🎤 Nói:**
> "**Bước 5: Lưu kết quả trong một transaction.** Lưu câu trả lời của AI, tăng số lượt trao đổi, cập nhật phiên và **ghi log sử dụng AI** (token vào/ra, độ trễ, chi phí) đều nằm trong một `@Transactional`. Bản log này giúp Admin theo dõi chi phí trên trang thống kê.
>
> Kết quả `AiMessageResponse` quay về FE. Service FE chuyển nó thành tin nhắn, lưu lại và hiển thị lên khung chat."

---

## Phần 3: Kết thúc phiên (~30 giây)

**🖥️ Màn hình:** [`AiTutorController.endSession`](file:///D:/holavietnamese-be/src/main/java/com/sep490/backend/modules/ai/controller/AiTutorController.java#L109-L116) → [`AiTutorService.endSession`](file:///D:/holavietnamese-be/src/main/java/com/sep490/backend/modules/ai/service/AiTutorService.java#L290-L305)

**🎤 Nói:**
> "Giai đoạn cuối trên diagram là kết thúc phiên. Backend cung cấp **`POST /api/ai/sessions/{id}/end`**: kiểm tra đúng chủ sở hữu, chuyển trạng thái sang **COMPLETED**, ghi thời điểm kết thúc và tóm tắt số lượt trao đổi.
>
> Em xin nói rõ: **API này đã hoàn thiện ở Backend, nhưng giao diện AI Tutor hiện chưa gắn nút gọi tới**. Đây là việc nhóm sẽ bổ sung ở giai đoạn tiếp theo. Em có thể demo API này bằng Postman."

---

## Kết luận (~30 giây)

**🖥️ Màn hình:** Quay lại sequence diagram.

**🎤 Nói:**
> "Tóm lại, luồng AI Tutor thể hiện rõ cách tổ chức của hệ thống:
> - **FE** tách giao diện, `AiTutorPage`, khỏi lớp gọi API, `aiTutorService` và `axiosClient`.
> - **BE** phân tầng rõ: Controller nhận request, Service xử lý nghiệp vụ, Repository làm việc với database.
> - **Bảo mật** dựa trên JWT: dữ liệu luôn được truy vấn theo user đang đăng nhập.
> - **Linh hoạt** nhờ định tuyến nhà cung cấp AI, và **ổn định** nhờ cơ chế dự phòng khi AI bên ngoài gặp lỗi.
>
> Em xin cảm ơn, em sẵn sàng nhận câu hỏi ạ."

---

## Câu hỏi có thể gặp: trả lời ngắn

| Câu hỏi | Trả lời |
|---|---|
| *"Trên diagram ghi trả UNAUTHORIZED khi người khác truy cập, code có làm vậy không?"* | "Khi gửi tin nhắn, code **không trả 403** mà **cô lập bằng cách gắn mã phiên với user hiện tại**, nên người khác không bao giờ chạm được dữ liệu. Lỗi `UNAUTHORIZED` được ném ở API sửa phiên (PATCH). Diagram nhóm sẽ cập nhật cho khớp code." |
| *"FE có chọn model AI không?"* | "FE có gửi tên model nhưng BE **không dùng**. BE lấy model từ cấu hình Admin để đảm bảo kiểm soát tập trung." |
| *"Nếu tạo phiên ở bước 1 bị lỗi thì sao?"* | "FE vẫn chờ request tạo phiên xong rồi mới gửi tin nhắn. Nếu phiên chưa có trong DB, `sendMessage` ở BE **tự tạo phiên** cho user hiện tại, nên học viên vẫn chat được." |
| *"Nút kết thúc phiên ở đâu?"* | "API `/end` đã có ở BE, **UI chưa gắn nút**. Em demo bằng Postman." |
| *"Gọi AI bên ngoài nằm trong transaction có vấn đề gì không?"* | "Hiện tại toàn bộ `sendMessage` nằm trong một transaction để đảm bảo dữ liệu nhất quán. Hướng cải tiến là tách lời gọi AI ra ngoài transaction để không giữ kết nối DB trong lúc chờ provider." |
