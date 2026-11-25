# KèoNhanh – project Android, tuần 4 (xây dựng chức năng)

Ứng dụng ghép kèo thể thao và board game quanh trường. Từ tuần 3 app dùng dữ liệu thật trên **Firebase** (Authentication + Cloud Firestore, gói Spark), kiến trúc **MVVM** (Fragment → ViewModel → Repository → Firebase). Tuần 4 nối nốt các màn còn dùng dữ liệu mẫu và thêm đăng ký, tìm kiếm, thông báo, gọi API.

## Mở và chạy project

1. Android Studio bản mới → **File › Open** → chọn thư mục repo. Chờ Gradle Sync.
2. Tải `google-services.json` của project Firebase `keo-nhanh` (Firebase Console › Project settings › Android app `com.keonhanh.app`) đặt vào `app/`. File này nằm trong `.gitignore`, không đưa lên GitHub.
3. Lần đầu dùng project Firebase mới, làm 2 việc trong Firebase Console (xem `docs/HUONG_DAN_TUAN3_MVVM.md` mục 5):
   - **Firestore › Rules**: dán nội dung `firestore.rules` rồi Publish.
   - **Firestore › Indexes › Single field**: thêm exemption cho collection group `members`, trường `uid`, bật *Collection group* Ascending. Thiếu index này thì Kèo của tôi, Danh sách chat và thông báo báo lỗi `FAILED_PRECONDITION`.
4. Chạy trên máy ảo hoặc máy thật Android 8.0 (API 26) trở lên. Máy ảo phải có Google Play (để quét QR bằng Google code scanner).

Cấu hình: Kotlin 2.0, compileSdk/targetSdk 35, minSdk 26, ViewBinding, Navigation component, Material 3 (XML Views). Thư viện tuần 4 khai báo một lần trong `gradle/libs.versions.toml`: Play services Location, Google code scanner, ZXing core, WorkManager, Retrofit + Gson, OkHttp logging, osmdroid.

## Tài khoản thử

| Email | Mật khẩu | Dùng cho |
| --- | --- | --- |
| k@gmail.com| 123456| Máy A: chủ kèo |
| h@gmail.com |123456 | Máy B: người xin vào |

## Chức năng tuần 4 và người làm

| Chức năng | Người làm | File chính |
| --- | --- | --- |
| Đăng ký (họ tên thật), đăng nhập, quên mật khẩu, giữ phiên | Minh Khoa | `ui/auth`, `AuthRepository`, `UserRepository` |
| Hồ sơ: sửa tên, lịch sử; check-in bằng GPS; `LocationRepository` | Minh Khoa | `ui/profile`, `data/repository/LocationRepository.kt` |
| Khám phá: ô tìm (bỏ dấu), lọc môn, bán kính, thời gian; khoảng cách thật | Huy Hoàng | `ui/explore`, `util/StringUtil.kt` |
| Tạo kèo có kiểm tra đầu vào; chọn điểm hẹn bằng API Nominatim | Huy Hoàng | `ui/create`, `data/remote/GeocodingApi.kt` |
| Thông báo: xin vào, được duyệt, tin nhắn mới, nhắc giờ kèo | Phong Hào | `notify/` |
| Danh sách chat dữ liệu thật | Phong Hào | `ui/chat` |
| Mã mời QR (ZXing), quét mã (Google code scanner), liên kết `keonhanh://moi/{code}` | Phong Hào | `ui/checkin/InviteLink.kt`, `InviteQrFragment`, `ScanQrFragment`, `MainActivity` |

## Thông báo hoạt động thế nào

Gói Spark không chạy được Cloud Functions nên không có server gửi push. App tự tạo thông báo trên máy:

- **App đang mở trên màn hình**: `InAppNotifier` nghe Firestore và báo ngay. Đang mở đúng phòng chat hoặc phòng chờ đó thì không báo.
- **App không ở trên màn hình**: `NotifyWorker` (WorkManager) kiểm tra khoảng **15 phút** một lần khi có mạng, nên thông báo nền có thể trễ tới 15 phút.
- **Nhắc giờ kèo**: `ReminderWorker` báo trước giờ hẹn 30 phút; rời kèo hoặc kèo bị hủy thì lịch nhắc tự hủy.
- Bấm thông báo → mở thẳng phòng chờ của kèo đó.

Khi thử thông báo nền: **bấm Home rồi chờ**, đừng bấm Stop trong Android Studio (Android coi là buộc dừng, chặn mọi việc chạy ngầm tới khi mở lại app). Máy Xiaomi, Oppo... cần tắt tối ưu pin cho KèoNhanh. Android 13 trở lên: app xin quyền thông báo một lần khi vào màn chính; từ chối thì app vẫn chạy, chỉ không có thông báo.

## Kịch bản demo (2 máy A và B)

Xem đầy đủ ở tài liệu benchmark tuần 4, mục 8. Tóm tắt phần thông báo và mã mời:

1. A tạo kèo có ngưỡng uy tín. B tìm thấy kèo, bấm **Xin vào** → máy A hiện thông báo.
2. A bấm thông báo → mở phòng chờ → **Duyệt** → máy B hiện thông báo "Bạn đã được duyệt".
3. B nhắn tin; A đang ở màn khác → nhận thông báo; tab **Chat** của A đưa kèo này lên đầu.
4. A mở **Mã mời** (icon QR ở phòng chờ); B vào Khám phá › icon quét → quét QR → mở đúng kèo. Không quét được thì gõ mã 6 ký tự.

## Kiểm thử

- Unit test (không cần máy ảo): `gradlew :app:testDebugUnitTest`.
- Test với Firebase Emulator (Repository, luật truy cập, màn hình): chạy `firebase emulators:start --only auth,firestore --project demo-keonhanh` ở thư mục gốc, rồi `gradlew :app:connectedDebugAndroidTest`. Chi tiết ở `docs/HUONG_DAN_TUAN3_MVVM.md` mục 9.

## Cấu trúc thư mục

```
app/src/main/java/com/keonhanh/app/
├── KeoNhanhApp.kt          tạo AppContainer, kênh thông báo, lịch NotifyWorker
├── MainActivity.kt         Activity duy nhất: NavHost, bottom nav, mở kèo từ thông báo / link mời
├── di/AppContainer.kt      nơi duy nhất tạo Firebase và các Repository
├── data/
│   ├── model/              dữ liệu để hiển thị (Keo, ChatRoom, UserProfile, CheckIn...)
│   ├── remote/             tên collection và trường, lớp ...Doc, chuyển đổi, dịch lỗi Firebase
│   ├── repository/         Auth, User, Keo, Member, Chat, Location
│   └── sample/             dữ liệu mẫu tuần 2 (đang bỏ dần)
├── notify/                 thông báo: NotificationHelper, InAppNotifier, NotifyWorker, ReminderWorker
├── ui/                     mỗi tính năng một package, thường gồm Fragment + ViewModel
│   ├── auth/ profile/ explore/ create/ lobby/ mykeo/ chat/ checkin/
│   └── common/             UiState, appViewModels, thẻ kèo, adapter dùng chung
└── util/                   hàm tiện ích giao diện
firestore.rules             luật truy cập Firestore
```

Quy ước đặt tên: layout `fragment_<màn>.xml`, `item_<dòng danh sách>.xml`, `view_<khối dùng lại>.xml`; id có tiền tố theo loại (`tv_`, `iv_`, `btn_`, `rv_`, `chip_`, `til_`, `et_`) để ViewBinding sinh tên dễ đọc (`binding.btnLogin`). Chuỗi mới để trong file `strings_<nhóm>.xml` của người làm (ví dụ `strings_notify.xml`) để tránh xung đột khi merge.

## Làm việc nhóm

- Mỗi người một nhánh (`Khoa`, `t4Hoang`, `tuan4/hao`), PR nhỏ theo từng việc, một người khác xem rồi mới merge vào `main`.
- File dùng chung (`AndroidManifest.xml`, `nav_graph.xml`, `strings.xml`, `AppContainer.kt`, `build.gradle.kts`, `libs.versions.toml`): nhắn nhóm trước khi sửa.

## Quy tắc layout (kiểm tra trước khi tạo pull request)

Ba lệnh sau phải **không in ra dòng nào**:

```bash
grep -rnE '="#[0-9A-Fa-f]{3,8}"' app/src/main/res/layout
grep -rn 'android:textSize=' app/src/main/res/layout
grep -rnE 'android:(text|hint)="[^@]' app/src/main/res/layout
```

Icon: Material Symbols Rounded (Apache 2.0). Font: Be Vietnam Pro (SIL OFL 1.1).
