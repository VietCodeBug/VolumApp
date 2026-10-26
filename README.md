# Floating Volume — Nút Âm Lượng Nổi Độc Lập Cho Android

Ứng dụng Android native (**Kotlin**) cung cấp **nút điều khiển âm lượng nổi (`TYPE_APPLICATION_OVERLAY`)** vận hành qua **Foreground Service độc lập với Activity**, giúp thay thế hoàn toàn hai phím cứng tăng/giảm âm lượng bị hỏng mà **không chứa quảng cáo, không cần kết nối Internet, không đòi quyền Root hay Accessibility Service**.

---

## 1. Tính năng nổi bật

- **Hoạt động độc lập khi vuốt đóng ứng dụng khỏi Recent Apps**:
  - `VolumeOverlayService` là một **started Foreground Service** khai báo `android:exported="false"`, `android:stopWithTask="false"` và `android:foregroundServiceType="specialUse"`.
  - Khi người dùng vuốt xóa **Floating Volume** khỏi màn hình đa nhiệm (Recent Apps), `onTaskRemoved` giữ nguyên service và cửa sổ nút nổi trên màn hình.
- **Hai chế độ thu gọn & Bảng mở rộng thông minh**:
  - **Thanh nhỏ cạnh màn hình (`+` / `−`)**: Bấm trực tiếp để tăng/giảm từng nấc âm lượng Media (`STREAM_MUSIC`), hoặc **nhấn giữ** để tăng/giảm liên tục (dừng ngay lập tức khi nhả tay hoặc hủy thao tác).
  - **Bong bóng biểu tượng loa**: Thu gọn gọn gàng trên màn hình; chạm vào để bung **Bảng điều khiển đầy đủ** gồm thanh trượt (SeekBar), chỉ số `hiện tại / tối đa (%)`, nút `−`, nút **Tắt tiếng / Mở tiếng nhanh (Mute/Unmute)** và nút `+`.
  - **Tự động thu gọn sau 4 giây**: Bảng mở rộng tự động thu gọn về bong bóng/thanh nhỏ sau 4 giây không thao tác.
- **Kéo thả mượt mà & Giữ trong vùng nhìn thấy**:
  - Phân biệt chính xác thao tác **Bấm (Tap)**, **Nhấn giữ (Long-press)** và **Kéo di chuyển (Drag)** theo ngưỡng `scaledTouchSlop` của thiết bị.
  - Tuỳ chọn **Tự động hít vào cạnh màn hình (Edge Snap)** và tự động tính toán lại tọa độ khi xoay ngang/dọc màn hình để nút không bao giờ bị đẩy ra ngoài vùng hiển thị.
  - Tuỳ chỉnh **Kích thước (`75% – 140%`)** và **Độ đậm (`35% – 100%`)** theo thời gian thực.
  - Cửa sổ overlay chỉ chiếm đúng diện tích nút/bảng (`WRAP_CONTENT`) với cờ `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL`, không chặn cảm ứng vùng ngoài và không chiếm focus bàn phím.

---

## 2. Yêu cầu hệ thống & Công nghệ

- **Ngôn ngữ & UI**: Kotlin, Jetpack Compose (Màn hình cấu hình Material 3) + Native Android Views trên `WindowManager` (cho cửa sổ Overlay độc lập).
- **SDK**:
  - `minSdk = 26` (Android 8.0 Oreo)
  - `targetSdk = 36` / `compileSdk = 36`
- **Quyền sử dụng tối thiểu**:
  - `android.permission.SYSTEM_ALERT_WINDOW` (Hiển thị trên ứng dụng khác)
  - `android.permission.MODIFY_AUDIO_SETTINGS` (Điều chỉnh âm lượng thiết bị)
  - `android.permission.FOREGROUND_SERVICE` & `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`
  - `android.permission.POST_NOTIFICATIONS` (Hiển thị thông báo trạng thái yên lặng kèm nút **Tắt** nhanh trên Android 13+)
  - **Không** yêu cầu quyền `INTERNET`, không quảng cáo, không theo dõi (analytics).

---

## 3. Hướng dẫn mở Project, Build và Cài đặt APK

### Cách 1: Mở bằng Android Studio
1. Mở **Android Studio** -> chọn **Open** và trỏ tới thư mục gốc của project này.
2. Đợi Gradle đồng bộ hoàn tất.
3. Vào menu **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. Sau khi build xong, file APK debug nằm tại:
   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```

### Cách 2: Build bằng dòng lệnh (CLI)
Tại thư mục gốc của project, chạy lệnh:
```bash
gradle assembleDebug
```
Hoặc kiểm tra kèm Unit Test / Robolectric Test và Lint:
```bash
gradle :app:testDebugUnitTest
gradle lintDebug
```
File APK xuất ra tại: `app/build/outputs/apk/debug/app-debug.apk`.

### Cài đặt APK vào điện thoại cá nhân
- Chép file `app-debug.apk` sang điện thoại và mở để cài đặt, hoặc cài qua cáp USB bằng ADB trên máy tính của bạn:
  ```bash
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```

---

## 4. Hướng dẫn thiết lập Quyền & Chạy nền (Quan trọng)

1. **Cấp quyền Hiển thị trên ứng dụng khác**:
   - Mở ứng dụng **Floating Volume** -> bấm **Mở trang cấp quyền** ở mục *Hiển thị trên ứng dụng khác* -> gạt bật cho phép **Floating Volume**.
2. **Bật nút âm lượng nổi**:
   - Quay lại ứng dụng và bấm **Bật nút âm lượng nổi**. Nút nổi sẽ xuất hiện ngay ở cạnh phải màn hình cùng thông báo yên lặng *"Nút âm lượng nổi đang hoạt động"*.
3. **Thiết lập Pin "Không hạn chế" (Unrestricted) trên các ROM quản lý nền mạnh**:
   - Trên các máy Xiaomi (MIUI/HyperOS), Samsung (One UI), OPPO (ColorOS), Vivo, Realme:
   - Bấm nút **Mở Thông tin ứng dụng** hoặc **Cài đặt Pin** ngay trong màn hình chính của app.
   - Chọn mục **Pin (Battery)** -> chuyển sang **Không hạn chế (Unrestricted)**.
   - Nếu máy có mục **Tự khởi chạy (Autostart)**, hãy bật cho phép để hệ thống không cưỡng bức dừng service khi bạn dọn dẹp đa nhiệm.

---

## 5. Giải thích rõ các giới hạn của Android & ROM

- **Vuốt app khỏi Recent Apps**: Nhờ `android:stopWithTask="false"` và kiến trúc tách rời `VolumeOverlayService` khỏi `MainActivity`, nút nổi vẫn tiếp tục hiển thị và bấm chỉnh được âm lượng sau khi vuốt đóng app khỏi màn hình đa nhiệm.
- **Hệ thống thu hồi bộ nhớ (Low RAM)**: Service trả về `START_STICKY` và lưu trạng thái `userEnabled` + vị trí nút. Khi hệ thống có đủ tài nguyên và khởi tạo lại service (`Intent == null`), nút nổi sẽ tự động phục hồi (tuy nhiên Android không cam kết thời điểm phục hồi tức thì nếu máy đang cạn kiệt RAM).
- **Buộc dừng (Force Stop) hoặc bấm Tắt**: Khi người dùng bấm **Tắt** (trong app hoặc từ nút **Tắt** trên thanh thông báo) hoặc bấm **Buộc dừng (Force Stop)** trong Cài đặt hệ thống, ứng dụng tôn trọng tuyệt đối quyết định của người dùng và dừng hẳn, không dùng vòng lặp ngầm hay báo thức để tự bật lại.
- **Màn hình bảo mật hệ thống**: Theo chính sách bảo mật của Android, các cửa sổ xác thực nhạy cảm (như hộp thoại cấp quyền hệ thống hoặc cài đặt APK) có thể tạm ẩn lớp phủ bên thứ ba; ứng dụng tuân thủ chuẩn bảo mật này của Android.

---

## 6. Danh sách kiểm tra thực tế (Verification Checklist)

1. Cấp quyền hiển thị nổi, bấm **Bật nút âm lượng nổi**, mở YouTube hoặc trình phát nhạc và bấm `+` / `−` hoặc kéo thanh trượt để kiểm tra âm lượng thực tế thay đổi.
2. Nhấn giữ nút `+` hoặc `−` để kiểm tra âm lượng tăng/giảm liên tục và dừng ngay khi nhả tay.
3. Nhấn nút Home / Back và **vuốt xóa Floating Volume khỏi Recent Apps**: kiểm tra nút nổi vẫn hiển thị và điều chỉnh được âm lượng.
4. Mở lại ứng dụng nhiều lần: xác nhận chỉ có duy nhất một nút nổi trên màn hình (không bị nhân bản).
5. Xoay ngang và xoay dọc màn hình: xác nhận nút nổi tự động kẹp vào trong vùng nhìn thấy.
6. Khi Activity đã bị vuốt đóng, kéo thanh thông báo xuống và bấm nút **Tắt**: xác nhận nút nổi biến mất và service dừng hoàn toàn.
