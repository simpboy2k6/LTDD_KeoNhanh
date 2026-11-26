# Tài liệu chi tiết các bài Test tự động – KèoNhanh

Dự án hiện có **83 test cases** chia làm **2 loại**:
1. **Unit Test (40 test cases, 7 file)** — nằm trong `app/src/test/java/com/keonhanh/app/`, chạy trực tiếp trên JVM của máy tính (không cần máy ảo, không cần Firebase).
2. **Instrumented / Emulator Test (43 test cases, 5 file test + 1 file hạ tầng)** — nằm trong `app/src/androidTest/java/com/keonhanh/app/emulator/`, chạy trên máy ảo Android kết nối tới **Firebase Emulator** (Auth + Firestore).

---

## Phần 1: Unit Test (40 tests)

**Cách chạy:**
```bash
gradlew :app:testDebugUnitTest
```

### 1. `KeoDocStatusTest.kt` (8 tests)
* **Vị trí:** `app/src/test/java/com/keonhanh/app/data/remote/KeoDocStatusTest.kt`
* **Mục đích:** Kiểm tra hàm `KeoDoc.effectiveStatus(nowMillis)` suy luận trạng thái kèo theo thời gian thực và số lượng người, cùng `sportEnum`.
* **Cách test hoạt động:** Tạo các `KeoDoc` giả lập với `startAt` và `deadlineAt` tính bằng phút so với mốc `NOW` (số âm = đã qua), truyền vào `effectiveStatus(NOW)` và so sánh kết quả:
  1. Còn chỗ và chưa tới hạn chốt $\rightarrow$ `KeoStatus.OPEN`.
  2. Đủ người (`memberCount == capacity`) trước hạn chốt $\rightarrow$ `KeoStatus.FULL`.
  3. Đã quá hạn chốt (`deadlineInMin = -1`) dù còn chỗ hay đủ chỗ $\rightarrow$ `KeoStatus.LOCKED`.
  4. Đúng ngay thời điểm hạn chốt (`deadlineInMin = 0`) $\rightarrow$ `KeoStatus.LOCKED`.
  5. Từ 15 phút trước tới 15 phút sau giờ hẹn (`startInMin` từ `15` xuống `-14`, đã qua hạn chốt) $\rightarrow$ `KeoStatus.CHECK_IN`; trước đó 16 phút $\rightarrow$ vẫn `LOCKED`.
  6. Quá giờ hẹn 15 phút (`startInMin = -15`, `-600`) $\rightarrow$ `KeoStatus.ENDED`.
  7. Trường `status = "ENDED"` trên Firestore luôn thắng mọi suy luận theo giờ $\rightarrow$ `KeoStatus.ENDED`.
  8. Tên môn lạ (ví dụ `"CHESS"`) rơi về mặc định `SOCCER` để app không bị crash.

---

### 2. `MappersTest.kt` (10 tests)
* **Vị trí:** `app/src/test/java/com/keonhanh/app/data/remote/MappersTest.kt`
* **Mục đích:** Kiểm tra các hàm chuyển đổi từ Firestore Doc (`KeoDoc`, `MessageDoc`) sang model UI (`Keo`, `Message`) và định dạng thời gian trong `Mappers.kt`.
* **Cách test hoạt động:** Truyền dữ liệu mẫu vào `formatCountdown`, `formatCheckInWindow`, `toKeo()`, `toMessage()` và kiểm tra kết quả:
  1. `formatCountdown`: dưới 1 giờ hiện `MM:SS` (`"24:10"`, `"00:59"`), từ 1 giờ trở lên hiện `H:MM:SS` (`"1:58:00"`).
  2. `formatCountdown` khi bằng `0` hoặc số âm $\rightarrow$ dừng ở `"00:00"`, không hiện số âm.
  3. `formatCheckInWindow`: giờ hẹn `17:00` $\rightarrow$ trả về `"16:45 – 17:15"`.
  4. `toKeo` lọc chỉ lấy thành viên `JOINED` (bỏ `REQUESTED`), sắp xếp chủ kèo (`HOST`) đứng đầu rồi mới tới thứ tự `joinedAt`.
  5. Xác định đúng `myRole`: `HOST`, `MEMBER`, hoặc `NONE` (khi đang `REQUESTED`, người lạ, hoặc `myUid = null`).
  6. Khi danh sách `members` truyền vào rỗng (như ở màn Khám phá), `Keo.memberCount` vẫn giữ đúng số từ Firestore.
  7. Thành viên có `checkInAt != null` được đánh dấu `checkedIn = true`.
  8. `deadlineCountdown` trên thẻ kèo tính đúng từ `deadlineAt` (ví dụ còn 45 phút $\rightarrow$ `"45:00"`).
  9. `MessageDoc.toMessage`: `isMine = true` nếu `senderId == myUid`, ngược lại `false`.
  10. Tin nhắn mới gửi chưa có giờ server (`sentAt = null`) không bị lỗi, trả về `time = ""`.

---

### 3. `DistanceMetersTest.kt` (5 tests)
* **Vị trí:** `app/src/test/java/com/keonhanh/app/data/repository/DistanceMetersTest.kt`
* **Mục đích:** Kiểm tra công thức Haversine (`distanceMeters` và `UserLocation.distanceTo`) dùng cho khoảng cách ở Khám phá và bán kính check-in GPS 100 m.
* **Cách test hoạt động:**
  1. Hai tọa độ trùng nhau $\rightarrow$ khoảng cách bằng `0.0`.
  2. Lệch 1 độ vĩ độ (`21.0` đến `22.0`) $\rightarrow$ xấp xỉ `111,195 m` (kiểm tra đúng đơn vị mét và đổi radian).
  3. Tính đối xứng: khoảng cách `A -> B` bằng `B -> A`.
  4. `UserLocation.distanceTo(...)` cho kết quả khớp với `distanceMeters(...)`.
  5. Tình huống check-in thực tế: điểm trong bán kính (`~89 m <= 100 m`) đạt chuẩn, điểm ngoài bán kính (`~111 m > 100 m`) bị từ chối.

---

### 4. `ReminderSchedulerTest.kt` (4 tests)
* **Vị trí:** `app/src/test/java/com/keonhanh/app/notify/ReminderSchedulerTest.kt`
* **Mục đích:** Kiểm tra tính thời gian trễ đặt lịch thông báo nhắc giờ kèo (`ReminderScheduler.delayMillis`) và định dạng giờ ở danh sách chat (`formatChatTime`).
* **Cách test hoạt động:**
  1. Kèo còn 120 phút $\rightarrow$ hẹn nhắc sau `90 phút` (tức trước giờ hẹn 30 phút).
  2. Vào kèo khi còn dưới 30 phút (ví dụ còn 10 phút) $\rightarrow$ trả về `0L` (nhắc ngay).
  3. Kèo đang bắt đầu hoặc đã qua (`<= now`) $\rightarrow$ trả về `null` (không đặt lịch nhắc).
  4. `formatChatTime`: tin nhắn trong ngày hôm nay hiện `"19:30"`, ngày hôm qua hiện `"17/11"`.

---

### 5. `InviteLinkTest.kt` (5 tests)
* **Vị trí:** `app/src/test/java/com/keonhanh/app/ui/checkin/InviteLinkTest.kt`
* **Mục đích:** Kiểm tra hàm tách mã mời `InviteLink.parse(...)` và tạo link `InviteLink.build(...)`.
* **Cách test hoạt động:**
  1. Link đầy đủ `"keonhanh://moi/AB12CD"` $\rightarrow$ trích xuất `"AB12CD"`.
  2. Mã gõ tay chữ thường kèm khoảng trắng/xuống dòng `"  ab12cd \n"` $\rightarrow$ chuẩn hóa thành `"AB12CD"`.
  3. Link viết hoa kèm dấu `/` ở cuối `"KEONHANH://MOI/xyz789/"` $\rightarrow$ `"XYZ789"`.
  4. `build` rồi `parse` ngược lại ra đúng mã ban đầu.
  5. Chuỗi `null`, rỗng, thiếu ký tự (5 ký tự), thừa ký tự (7 ký tự), chứa ký tự đặc biệt, hoặc URL domain khác $\rightarrow$ trả về `null`.

---

### 6. `PrimaryActionTest.kt` (6 tests)
* **Vị trí:** `app/src/test/java/com/keonhanh/app/ui/lobby/PrimaryActionTest.kt`
* **Mục đích:** Kiểm tra hàm `primaryActionFor(keo, myState)` quyết định nút chính ở đáy màn hình S15 (Phòng chờ) theo ma trận *Trạng thái kèo × Vai trò của mình*.
* **Cách test hoạt động:**
  1. Kèo `OPEN`, người ngoài (`NONE`): không ngưỡng $\rightarrow$ `JOIN`; có `minTrust` $\rightarrow$ `REQUEST_JOIN`.
  2. Kèo `OPEN` hoặc `FULL`, đang chờ duyệt (`REQUESTED`) $\rightarrow$ `CANCEL_REQUEST`.
  3. Kèo `OPEN` hoặc `FULL`: thành viên (`MEMBER`) $\rightarrow$ `LEAVE`; chủ kèo (`HOST`) $\rightarrow$ `CANCEL_KEO`.
  4. Kèo `FULL`, người ngoài (`NONE`) $\rightarrow$ `FULL` (nút bị khóa `enabled = false`).
  5. Kèo `LOCKED`: người ngoài $\rightarrow$ `LOCKED`, thành viên $\rightarrow$ `LOCKED_MEMBER` (bị khóa), chủ kèo $\rightarrow$ `CANCEL_KEO` (vẫn hủy được).
  6. Kèo `CHECK_IN` và `ENDED`: mọi vai trò đều trả về `CHECKING_IN` / `ENDED`.

---

### 7. `StringUtilTest.kt` (2 tests)
* **Vị trí:** `app/src/test/java/com/keonhanh/app/util/StringUtilTest.kt`
* **Mục đích:** Kiểm tra hàm `StringUtil.removeAccent(...)` phục vụ tìm kiếm không phân biệt dấu tiếng Việt ở màn Khám phá.
* **Cách test hoạt động:**
  1. Chuyển `"Cầu lông"`, `"Đá bóng sân 7 người"`, `"Kèo Nhanh Đà Nẵng"` thành chuỗi không dấu (kể cả chữ `Đ/đ`).
  2. Tìm `"cau long"` khớp với `"Giao lưu Cầu Lông tối thứ 5"`.

---

## Phần 2: Instrumented / Emulator Test (43 tests)

**Cách chạy:**
1. Mở terminal tại gốc repo: `firebase emulators:start --only auth,firestore --project demo-keonhanh`
2. Mở máy ảo Android (kết nối qua `10.0.2.2`).
3. Chạy `gradlew :app:connectedDebugAndroidTest`.

> **Hạ tầng dùng chung (`FirebaseEmulator.kt`):** Tạo nhiều tài khoản độc lập (`host`, `b`, `c`, `APP_USER`) chạy đồng thời trên máy ảo nối vào Auth & Firestore Emulator; có hàm `adminSetTimes` gọi REST API của Emulator với quyền `Bearer owner` để dời giờ kèo về quá khứ/giờ check-in phục vụ test, và 2 hàm kiểm tra lỗi `assertDenied` (`PERMISSION_DENIED`) / `assertAppError` (`AppException`).

---

### 8. `FirestoreRulesEmulatorTest.kt` (15 tests)
* **Vị trí:** `app/src/androidTest/java/com/keonhanh/app/emulator/FirestoreRulesEmulatorTest.kt`
* **Mục đích:** Kiểm tra bảo mật trong `firestore.rules` bằng cách cố tình ghi/đọc sai luật trực tiếp vào Firestore (bỏ qua Repository) và xác nhận mọi hành vi gian lận đều bị chặn (`PERMISSION_DENIED`).
* **Các nhóm test:**
  * **Chưa đăng nhập:** Khách (`signedOut`) đọc kèo hoặc danh sách thành viên $\rightarrow$ bị chặn.
  * **Sửa/xóa kèo:** Thành viên hoặc người ngoài sửa tên, xóa kèo $\rightarrow$ bị chặn; Chủ kèo sửa tên/số chỗ được phép nhưng nếu cố sửa kèm `memberCount`, `inviteCode`, `hostId` $\rightarrow$ bị chặn.
  * **Tạo kèo hợp lệ:** Tạo kèo mà không tạo đồng thời document `HOST` trong `members/`, đứng tên `hostId` người khác, đặt `memberCount != 1`, `capacity < 2`, hoặc tên `< 3` ký tự $\rightarrow$ bị chặn.
  * **Gian lận `memberCount` & `members`:** Tự sửa `memberCount = 99` hoặc increment/decrement không kèm thêm/xóa member; tự thêm `JOINED` mà không tăng `memberCount`; thêm hộ người khác; tự phong `HOST`; chen vào kèo đã đầy; vào thẳng hoặc tự duyệt kèo có `minTrust`; đuổi người khác hoặc rời kèo sau hạn chốt $\rightarrow$ đều bị chặn.
  * **Chat (`messages`):** Người ngoài và người đang xin vào (`REQUESTED`) đọc/gửi tin $\rightarrow$ bị chặn; mạo danh `senderId`, sửa tin, xóa tin người khác, gửi tin rỗng, hoặc tự đặt giờ quá khứ thay cho `serverTimestamp()` $\rightarrow$ bị chặn.
  * **Hủy kèo (`batch`):** Chủ kèo xóa toàn bộ `members`, `messages` và document kèo trong cùng 1 batch $\rightarrow$ thành công.
  * **Check-in (Tuần 4):** Thành viên ghi `checkInAt`, `checkInDistanceM <= 100`, `attendance = "PRESENT"` đúng luật $\rightarrow$ thành công; gian lận khoảng cách `150 m > 100 m`, check-in hộ người khác, hoặc check-in lần 2 $\rightarrow$ bị chặn.

---

### 9. `MemberRepositoryEmulatorTest.kt` (13 tests)
* **Vị trí:** `app/src/androidTest/java/com/keonhanh/app/emulator/MemberRepositoryEmulatorTest.kt`
* **Mục đích:** Kiểm tra `FirestoreMemberRepository` chạy thật với Firestore Emulator (đặc biệt là tính toàn vẹn của Transaction).
* **Cách test hoạt động:**
  1. `join` tạo đúng document `members/{uid}` (`JOINED`, `MEMBER`) và tăng `memberCount` từ 1 lên 2.
  2. Tham gia 2 lần báo `error_already_member`, `memberCount` không đổi.
  3. Kèo đầy báo `error_keo_full`, không tạo document thành viên.
  4. **Tranh chỗ cuối đồng thời:** Cho 2 tài khoản `b` và `c` gọi `join` song song trên 2 coroutine (`Dispatchers.IO`) vào kèo chỉ còn 1 chỗ (lặp 3 lần) $\rightarrow$ đúng 1 người thành công, `memberCount == 2` khớp số document.
  5. Kèo có `minTrust`: `join` bị chặn (`error_need_request`), `requestJoin` tạo `REQUESTED` (chưa tăng `memberCount`), `approve` chuyển thành `JOINED` và tăng `memberCount`.
  6. `reject` xóa yêu cầu xin vào, `memberCount` giữ nguyên.
  7. Người xin vào tự hủy yêu cầu (`leave` khi `REQUESTED`) xóa document, `memberCount` giữ nguyên.
  8. Rời kèo (`leave` khi `JOINED`) xóa document, giảm `memberCount`, và có thể `join` lại nếu còn chỗ.
  9. Chủ kèo không rời được (`error_host_cannot_leave`), người ngoài không rời được (`error_not_member`).
  10. Sau hạn chốt: không vào được (`error_keo_closed`), không rời được (`error_leave_locked`).
  11. Kèo không tồn tại báo `error_keo_not_found`.
  12. `observeMembers` nhận cập nhật realtime khi có người mới tham gia.
  13. `observeMyMemberships` (truy vấn `collectionGroup("members")`) liệt kê đúng các kèo mình đã tham gia để phục vụ màn Kèo của tôi / Lịch sử.

---

### 10. `ChatRepositoryEmulatorTest.kt` (6 tests)
* **Vị trí:** `app/src/androidTest/java/com/keonhanh/app/emulator/ChatRepositoryEmulatorTest.kt`
* **Mục đích:** Kiểm tra `FirestoreChatRepository` và các hàm đọc `observeKeo` / `getKeos` của `FirestoreKeoRepository`.
* **Cách test hoạt động:**
  1. Gửi nhiều tin từ 2 tài khoản khác nhau $\rightarrow$ người kia nhận qua `observeMessages` đúng thứ tự và đã `trim()` khoảng trắng.
  2. `observeLatestMessage` trả về `null` khi chưa có tin, và cập nhật tin mới nhất cho thẻ xem trước ở S15.
  3. `delete` xóa đúng tin của mình khỏi Firestore.
  4. Tin chỉ có khoảng trắng bị bỏ qua; tin dài `1001` ký tự báo `error_message_too_long`; đúng `1000` ký tự gửi thành công.
  5. `observeKeo` nhận đúng dữ liệu kèo và tự phát `null` khi kèo bị xóa.
  6. `getKeos` đọc nhiều kèo theo danh sách ID, tự bỏ qua ID không tồn tại/trùng lặp, và trả về rỗng khi truyền danh sách rỗng.

---

### 11. `KeoRepositoryEmulatorTest.kt` (1 test)
* **Vị trí:** `app/src/androidTest/java/com/keonhanh/app/emulator/KeoRepositoryEmulatorTest.kt`
* **Mục đích:** Kiểm tra `KeoRepository.createKeo` tạo kèo trên Firestore Emulator và ánh xạ đúng tên chủ kèo.
* **Cách test hoạt động:** Gọi `createKeo(draft, host.uid, "Phong Hào")`, sau đó đọc lại danh sách thành viên (`members`) và `KeoDoc.toKeo(...)` để xác nhận có document `HOST`/`JOINED` và `keo.hostName == "Phong Hào"`.

---

### 12. `ScreensEmulatorTest.kt` (8 tests)
* **Vị trí:** `app/src/androidTest/java/com/keonhanh/app/emulator/ScreensEmulatorTest.kt`
* **Mục đích:** Test giao diện End-to-End (mở `MainActivity` thật trên máy ảo kết nối Firebase Emulator, tự động bấm nút, nhập chữ, chuyển tab, kiểm tra hiển thị và chụp ảnh màn hình vào `files/test-shots/`).
* **Các kịch bản test:**
  1. `s15_nguoiNgoai_thamGia_chat_roiKeo`: Mở S15 ở vai trò người ngoài $\rightarrow$ bấm "Tham gia kèo" $\rightarrow$ mở S16 gửi tin và nhận tin realtime từ máy khác $\rightarrow$ quay lại S15 bấm "Rời kèo" và xác nhận hộp thoại.
  2. `s15_keoCoNguong_xinVao_roiDuocChuKeoDuyet`: Mở kèo có `minTrust = 70` $\rightarrow$ bấm "Xin vào" $\rightarrow$ kiểm tra nút đổi thành "Hủy yêu cầu xin vào" và `memberCount` chưa tăng $\rightarrow$ chủ kèo duyệt $\rightarrow$ nút đổi thành "Rời kèo".
  3. `s15_chuKeo_thayNutHuyKeo_vaDuyetNguoiXinVao`: Chủ kèo thấy nút "Hủy kèo", mở menu "Duyệt người xin vào (1)" duyệt thành viên mới.
  4. `s15_daChot_thanhVienKhongConNutRoi`: Dời hạn chốt về quá khứ $\rightarrow$ nút đổi thành `"Đã chốt · vắng mặt tính là bùng kèo"` (bị khóa), ẩn đồng hồ đếm ngược.
  5. `s15_trongGioCheckIn_hienTheCheckIn`: Dời giờ hẹn vào cửa sổ check-in $\rightarrow$ kiểm tra thẻ check-in hiện ra, đếm `"0/2 đã check-in"`, nút chính `"Check-in"`, chạy ổn định qua nhiều nhịp đồng hồ 1 giây.
  6. `s15_keoKhongTonTai_hienLoi_khongCrash`: Mở kèo không tồn tại $\rightarrow$ hiện màn hình lỗi `"Kèo không còn tồn tại"`, không crash.
  7. `chuaDangNhap_moS15vaS17_hienThongBao_khongCrash`: Đăng xuất rồi mở S15 và S17 $\rightarrow$ hiện thông báo lỗi quyền/yêu cầu đăng nhập, không crash.
  8. `s17_keoCuaToi_baTab`: Dựng dữ liệu ở cả 3 trạng thái $\rightarrow$ kiểm tra tab "Đang tham gia", "Đang làm chủ", "Đã xong", kiểm tra danh sách tự cập nhật realtime khi rời kèo từ máy khác.

