# Floating Volume — Nút Âm Lượng Nổi Độc Lập Cho Android

Ứng dụng Android native giúp thay thế hoàn toàn hai phím cứng tăng/giảm âm lượng bị hỏng bằng một nút điều khiển nổi trên màn hình. Ứng dụng vận hành qua Foreground Service độc lập với Activity, không chứa quảng cáo, không cần kết nối mạng hay quyền nhạy cảm, và tiếp tục hoạt động ổn định ngay cả khi người dùng vuốt đóng ứng dụng khỏi danh sách đa nhiệm (Recent Apps).

### User Review & Critical Decisions

> [!IMPORTANT]
> Dưới đây là các lựa chọn trải nghiệm đã được xác nhận cùng các quyết định kỹ thuật quan trọng để đảm bảo nút nổi hoạt động bền bỉ đúng chuẩn Android 8.0 – Android 15+:

- **Quyết định đã xác nhận 1 — Tự động thu gọn bảng mở rộng**: Khi người dùng mở bảng điều khiển đầy đủ (chứa thanh trượt và nút Mute) từ bong bóng loa, bảng sẽ **tự động thu gọn về bong bóng sau 4 giây không thao tác** (bộ đếm thời gian sẽ được làm mới mỗi khi chạm hoặc kéo thanh trượt).
- **Quyết định đã xác nhận 2 — Chế độ tự hít cạnh màn hình (Edge Snap)**: Người dùng có thể **bật/tắt tuỳ chọn tự động hít vào mép trái/phải màn hình** ngay trong phần cài đặt ứng dụng. Khi bật, nút nổi sẽ trượt mượt về mép gần nhất sau khi thả tay; khi tắt, nút giữ nguyên tại vị trí bất kỳ trong vùng hiển thị an toàn.
- **Quyết định đã xác nhận 3 — Tiện ích trong bảng mở rộng**: Bổ sung **nút Tắt tiếng nhanh (Mute / Unmute)** bên cạnh hai nút `+` / `−` và thanh trượt âm lượng Media thực tế của thiết bị.
- **Cơ chế Foreground Service & Tương thích Android 14/15+**: Sử dụng `FOREGROUND_SERVICE_SPECIAL_USE` kèm khai báo `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` giải thích rõ mục đích trợ năng/điều khiển âm lượng phần cứng hỏng, kết hợp `android:stopWithTask="false"` và `START_STICKY` để tồn tại độc lập khi Activity bị huỷ hoặc bị vuốt khỏi Recent Apps.

---

### 1. Overview & Core Concept

- **Chức năng cốt lõi**: Cung cấp giao diện nút âm lượng nổi (`TYPE_APPLICATION_OVERLAY`) bám trên màn hình và hoạt động xuyên suốt trên mọi ứng dụng khác (như YouTube, trình duyệt, game). Hỗ trợ hai chế độ hiển thị linh hoạt:
  1. **Thanh nhỏ cạnh màn hình (Compact Pill Bar)**: Gồm hai nút `+` và `−` bấm trực tiếp (hoặc nhấn giữ để tăng/giảm liên tục) kèm nút chuyển sang bong bóng/mở bảng.
  2. **Bong bóng thu gọn (Collapsed Speaker Bubble)**: Một biểu tượng loa nhỏ gọn; khi chạm vào sẽ bung ra **Bảng điều khiển đầy đủ (Expanded Panel)** có chỉ số âm lượng `hiện tại / tối đa`, thanh trượt (Slider) theo đúng số nấc thực tế của máy, nút `+` / `−`, nút **Tắt tiếng nhanh (Mute/Unmute)** và nút thu gọn.
- **Đối tượng sử dụng**: Người dùng cá nhân có thiết bị hỏng phím âm lượng vật lý, cần một công cụ gọn nhẹ, phản hồi tức thì, không quảng cáo làm phiền và không đòi hỏi quyền Root hay Accessibility Service.
- **Giá trị mang lại**: Giải quyết triệt để lỗi mất nút nổi khi vuốt tắt app ở màn hình đa nhiệm nhờ kiến trúc tách biệt hoàn toàn vòng đời của `VolumeOverlayService` khỏi `MainActivity`.

---

### 2. User Experience & Visual Design

- **Hành trình người dùng (Key User Flows)**:
  1. **Khởi chạy & Cấp quyền**: Mở ứng dụng -> Xem thẻ trạng thái quyền "Hiển thị trên ứng dụng khác" (`SYSTEM_ALERT_WINDOW`) và quyền Thông báo (trên Android 13+) -> Bấm nút mở thẳng trang cài đặt hệ thống của ứng dụng để cấp quyền.
  2. **Bật nút âm lượng nổi**: Bấm nút lớn **Bật nút âm lượng nổi** -> `VolumeOverlayService` khởi chạy ngay lập tức với thông báo Foreground yên lặng ("Nút âm lượng nổi đang hoạt động" kèm nút hành động **Tắt**) và hiển thị nút nổi trên màn hình.
  3. **Tuỳ chỉnh trực quan theo thời gian thực**: Điều chỉnh chế độ hiển thị mặc định (Thanh nhỏ `+`/`−` hoặc Bong bóng loa), kích thước (Nhỏ / Vừa / Lớn hoặc tỉ lệ % tuỳ chỉnh), độ trong suốt (Opacity), vị trí ban đầu, và công tắc **Tự động hít cạnh màn hình**. Mọi thay đổi được áp dụng trực tiếp lên nút nổi đang hiển thị mà không tạo bản sao trùng lặp.
  4. **Tối ưu chạy nền & Kiểm tra**: Xem trạng thái pin hiện tại ("Đã tối ưu chạy nền" hoặc "Đang bị giới hạn") kèm nút mở trang Cài đặt tối ưu hoá pin / Thông tin ứng dụng và danh sách hướng dẫn kiểm tra thực tế.
- **Định hướng thẩm mỹ & Giao diện (Visual Identity & Theme)**:
  - *Aesthetic Direction*: Phong cách **Utilitarian Tactile Hardware** — tối giản, hiện đại, lấy cảm hứng từ cụm phím vật lý cao cấp với độ tương phản rõ nét, các nút bấm lớn (tối thiểu `48dp x 48dp`) dễ thao tác bằng một ngón tay.
  - *Color Palette & Mood*: Hỗ trợ giao diện Sáng/Tối theo Material Design 3 kết hợp bảng màu Slate & Cobalt Teal sắc nét:
    - Nền chính (`--background` / `surface`): `#0F172A` (Dark) / `#F8FAFC` (Light)
    - Nền thẻ & Bảng nổi (`--card` / `surfaceContainerHigh`): `#1E293B` (Dark) / `#FFFFFF` (Light) với viền mảnh tinh tế
    - Điểm nhấn chính (`--primary`): `#0EA5E9` (Sky Cobalt) cho trạng thái đang hoạt động và thanh trượt âm lượng
    - Trạng thái cảnh báo / Tắt tiếng (`--accent` / `error`): `#F59E0B` (Amber cho nhắc quyền) & `#EF4444` (Coral Red cho trạng thái Mute / Dừng)
  - *Typography & Hierarchy*: Kết hợp phông chữ hiển thị hiện đại, rõ nét cho tiêu đề và chỉ số phần trăm âm lượng cùng phông chữ dễ đọc cho hướng dẫn tiếng Việt, tuân thủ đơn vị `sp` hỗ trợ phóng chữ hệ thống.
  - *Component Styling & Layout*: Giao diện màn hình chính chia thành các thẻ chức năng phân tách rõ ràng (Thẻ Trạng thái & Công tắc chính, Thẻ Cấp quyền & Chạy nền, Thẻ Tuỳ chỉnh Giao diện Nút nổi, Thẻ Giải thích Giới hạn Hệ thống & Hướng dẫn Kiểm tra).
- **Tương tác & Phản hồi chuyển động (Interactive Feedback & Motion)**:
  - Phân biệt chính xác thao tác **Chạm (Tap)**, **Nhấn giữ (Long-press repeat)** và **Kéo di chuyển (Drag)** bằng ngưỡng `touchSlop` chuẩn của hệ thống.
  - Khi nhấn giữ nút `+` hoặc `−`, âm lượng tăng/giảm liên tục theo chu kỳ mượt mà và dừng ngay lập tức tại khoảnh khắc nhả tay (`ACTION_UP`), huỷ chạm (`ACTION_CANCEL`), hoặc chạm tới ngưỡng `min/max`.
  - Khi xoay màn hình (Portrait <-> Landscape) hoặc thay đổi kích thước hiển thị, nút nổi tự động tính toán lại biên độ và kẹp (clamp) tọa độ để luôn nằm trọn trong vùng nhìn thấy.

---

### 3. Key Product Decisions & Trade-Offs

- **Quyết định 1: Sử dụng Native Android Views trong WindowManager cho Overlay thay vì gắn vào Lifecycle của Activity**
  - *Giải pháp chọn*: Khởi tạo cây giao diện của nút nổi trực tiếp bằng `Context` của `VolumeOverlayService` và quản lý qua `WindowManager` với cờ `TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE`, `FLAG_NOT_TOUCH_MODAL`, và `PixelFormat.TRANSLUCENT`.
  - *Lý do*: Siêu nhẹ, không chiếm focus bàn phím của ứng dụng bên dưới, chỉ nhận sự kiện chạm đúng trong diện tích của nút/bảng điều khiển, và hoàn toàn miễn nhiễm với việc `MainActivity` bị huỷ khi vuốt khỏi Recent Apps.
  - *Phương án đã loại bỏ*: Popup gắn với Activity hoặc Accessibility Service (vi phạm yêu cầu không đòi quyền Accessibility nhạy cảm).
- **Quyết định 2: Đồng bộ trạng thái thực tế của Service và Âm lượng thiết bị**
  - *Giải pháp chọn*: Lưu cấu hình người dùng (`userEnabled`, tọa độ `x/y`, chế độ thu gọn, kích thước, độ trong suốt, chế độ hít cạnh) vào `SharedPreferences` kết hợp `StateFlow` trạng thái thực tế từ `VolumeOverlayService` và `BroadcastReceiver` lắng nghe biến động âm lượng hệ thống (`android.media.VOLUME_CHANGED_ACTION`).
  - *Lý do*: Đảm bảo giao diện trong ứng dụng luôn phản ánh đúng việc Service có đang thực sự chạy và gắn overlay hay không, đồng thời thanh trượt luôn khớp với mức âm lượng `STREAM_MUSIC` thực tế của thiết bị (kể cả khi âm lượng thay đổi bởi tai nghe Bluetooth hoặc ứng dụng khác).
- **Quyết định 3: Xử lý đúng vòng đời khi vuốt Recent Apps (`onTaskRemoved`) vs. khi bấm Tắt chủ động**
  - *Giải pháp chọn*: Trong `onTaskRemoved()`, tuyệt đối không gọi `stopSelf()` hay gỡ overlay; giữ nguyên Foreground Service và cập nhật lưu vị trí hiện tại. Khi người dùng bấm **Tắt** (từ trong app hoặc từ nút hành động trên Notification), đặt `userEnabled = false`, gỡ view khỏi `WindowManager` an toàn, gọi `stopForeground(STOP_FOREGROUND_REMOVE)` và `stopSelf()`.
  - *Lý do*: Đáp ứng yêu cầu quan trọng nhất là vuốt xóa app khỏi Recent Apps vẫn giữ nguyên nút nổi, đồng thời tôn trọng tuyệt đối quyết định dừng chủ động của người dùng (không dùng vòng lặp restart hay giữ WakeLock).

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

#### Architecture & Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                       MAIN ACTIVITY (Jetpack Compose)                   │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌───────────────┐  │
│  │ Permission & Battery │  │  Service Master      │  │ Overlay Style │  │
│  │ Setup Cards          │  │  Toggle & Live State │  │ Customizer    │  │
│  └──────────┬───────────┘  └──────────┬───────────┘  └───────┬───────┘  │
└─────────────┼─────────────────────────┼──────────────────────┼──────────┘
              │ System Intents          │ Start / Stop / Sync  │ Prefs Update
              ▼                         ▼                      ▼
┌─────────────────────────┐  ┌────────────────────────────────────────────┐
│ ANDROID SYSTEM SERVICES │  │        OVERLAY PREFERENCES STORE           │
│ • Settings.canDrawOver… │  │ • userEnabled (Boolean)                    │
│ • AudioManager (MUSIC)  │  │ • displayMode (COMPACT_BAR | BUBBLE)       │
│ • WindowManager         │  │ • savedX, savedY, snapToEdge               │
│ • PowerManager          │  │ • scaleFactor, opacityAlpha                │
└─────────────▲───────────┘  └────────────────────▲───────────────────────┘
              │                                   │ Read / Persist
              │ Audio & Window APIs               ▼
┌─────────────┴───────────────────────────────────────────────────────────┐
│           VOLUME OVERLAY SERVICE (Started Foreground Service)           │
│           [exported="false", stopWithTask="false", specialUse]          │
│                                                                         │
│  ┌──────────────────────────────┐   ┌────────────────────────────────┐  │
│  │ Foreground Notification Mgr  │   │ Overlay Window Controller      │  │
│  │ • Silent Low-Importance Chan │   │ • Single Instance View Guard   │  │
│  │ • ContentIntent -> MainAct   │   │ • Drag vs Tap Touch Classifier │  │
│  │ • ActionIntent -> ACTION_STOP│   │ • Hold-to-Repeat Coroutine/Hdlr│  │
│  └──────────────────────────────┘   │ • 4s Auto-Collapse Timer       │  │
│                                     │ • Orientation Clamp & EdgeSnap │  │
│                                     └────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

#### Data Model & State
- **OverlayConfigState**:
  - `isServiceRunning: Boolean`: Trạng thái thực tế của `VolumeOverlayService` và view đang gắn trên `WindowManager`.
  - `userEnabled: Boolean`: Ý định bật/tắt của người dùng được lưu bền vững để phục hồi khi hệ thống khởi tạo lại service (`START_STICKY` với `intent == null`).
  - `initialMode: OverlayMode`: Chế độ hiển thị (`COMPACT_BAR` — thanh nhỏ `+`/`−` mặc định, hoặc `COLLAPSED_BUBBLE` — bong bóng loa).
  - `isPanelExpanded: Boolean`: Trạng thái mở bảng đầy đủ (có thanh trượt, chỉ số âm lượng, nút Mute), tự đóng sau 4 giây không tương tác.
  - `positionX: Int`, `positionY: Int`: Tọa độ nút nổi trên màn hình, luôn được kẹp trong giới hạn `[0 .. screenWidth - viewWidth]` và `[0 .. screenHeight - viewHeight]`.
  - `snapToEdge: Boolean`: Tự động hít về mép trái/phải sau khi kết thúc kéo thả.
  - `sizePercent: Int`: Kích thước hiển thị (từ `75%` đến `140%`, mặc định `100%`).
  - `opacityPercent: Int`: Độ đậm nhạt của nút nổi (từ `35%` đến `100%`, mặc định `90%`).
- **VolumeState**:
  - `currentVolume: Int`, `maxVolume: Int`, `minVolume: Int`: Lấy trực tiếp từ `AudioManager.STREAM_MUSIC` (trên Android P+ dùng `getStreamMinVolume`, các bản thấp hơn là `0`).
  - `isMuted: Boolean`, `lastNonZeroVolume: Int`: Phục vụ nút **Tắt tiếng nhanh (Mute / Unmute)** trong bảng mở rộng để khôi phục đúng mức âm lượng trước khi tắt tiếng.

#### Interactive Component & State Mapping
- **Thao tác trên Nút nổi (Overlay Window Controller)**:
  - *Bấm đơn nút `+` / `−`*: Đọc mức âm lượng `STREAM_MUSIC` hiện tại từ `AudioManager`, cộng/trừ `1` nấc trong phạm vi `[minVolume, maxVolume]`, gọi `setStreamVolume`, cập nhật ngay chỉ số và thanh trượt trên bảng.
  - *Nhấn giữ nút `+` / `−`*: Sau khoảng trễ khởi đầu `350ms` (nếu ngón tay không kéo vượt ngưỡng `touchSlop`), kích hoạt bộ lặp mỗi `110ms` để tăng/giảm liên tục. Dừng tức thì khi nhận `ACTION_UP`, `ACTION_CANCEL`, khi ngón tay rời vùng nút hoặc khi đạt mức `min/max`.
  - *Kéo di chuyển (Drag)*: Khi khoảng cách di chuyển vượt `touchSlop`, huỷ bộ đếm nhấn giữ, chuyển sang chế độ kéo và cập nhật `WindowManager.updateViewLayout()` theo thời gian thực. Khi thả tay (`ACTION_UP`), nếu `snapToEdge == true` thì tính mép gần nhất và đưa nút về mép đó, sau đó lưu tọa độ vào `SharedPreferences`.
  - *Mở bảng & Tự động thu gọn 4 giây*: Khi chạm vào bong bóng loa (hoặc nút mở rộng trên thanh nhỏ), đọc lại mức âm lượng thực tế từ `AudioManager`, hiển thị bảng điều khiển kèm thanh trượt và nút Mute, đồng thời khởi động bộ đếm `4000ms`. Bất kỳ thao tác chạm/kéo thanh trượt nào trên bảng đều đặt lại bộ đếm `4000ms`; khi hết giờ, tự động thu gọn về trạng thái trước đó.
- **Điều khiển Vòng đời Service (`VolumeOverlayService`)**:
  - `ACTION_START` hoặc `intent == null` (do hệ thống tái tạo sau khi thiếu bộ nhớ): Kiểm tra `userEnabled == true` và `Settings.canDrawOverlays(this)`. Nếu hợp lệ, gọi `startForeground()` ngay lập tức, đảm bảo chỉ tạo duy nhất 1 overlay view (nếu đã tồn tại thì chỉ cập nhật giao diện). Nếu quyền overlay bị thu hồi, ghi nhận trạng thái tắt an toàn và dừng service để không gây crash.
  - `ACTION_UPDATE_CONFIG`: Áp dụng ngay kích thước, độ trong suốt, chế độ hiển thị, hoặc đặt lại vị trí mặc định lên view hiện tại.
  - `ACTION_STOP` (từ nút Tắt trên màn hình chính hoặc nút "Tắt" trên Notification): Đặt `userEnabled = false`, huỷ mọi callback/handler nhấn giữ và tự thu gọn, gỡ view khỏi `WindowManager`, gọi `stopForeground(STOP_FOREGROUND_REMOVE)` và `stopSelf()`.
