# Tuần 3 – Khung MVVM và cách làm tiếp (Phong Hào viết cho cả nhóm)

Nhánh: `tuan3/hao`. Đọc mục 1–3 trước, rồi tới phần việc của mình (mục 4 cho Huy Hoàng, mục 6 cho Minh Khoa).

## 1. Đã có gì

| File | Làm gì | Ai giữ |
| --- | --- | --- |
| `di/AppContainer.kt` | Tạo Firebase và mọi Repository, một lần khi mở app | Phong Hào |
| `ui/common/FragmentExt.kt` | `appViewModels { }` tạo ViewModel có tham số; `collectWhenStarted(flow) { }` nghe dữ liệu trong Fragment | Phong Hào |
| `ui/common/UiState.kt` | `UiState` (Loading / Success / Error), `UiText`, `toUiText()` | Phong Hào |
| `data/repository/AppException.kt`, `data/remote/FirebaseErrors.kt` | Lỗi nghiệp vụ có câu thông báo sẵn; dịch lỗi Firebase (mạng, không có quyền) sang câu thông báo | Phong Hào |
| `data/remote/FirestorePaths.kt` | Tên collection và tên trường; **không gõ chuỗi "keos", "members"... ở chỗ khác** | Phong Hào |
| `data/remote/FirestoreDocs.kt` | `KeoDoc`, `MemberDoc`, `MessageDoc`, `KeoDraft`: bản sao đúng như document trên Firestore | Phong Hào |
| `data/remote/Mappers.kt` | Firestore ↔ Doc ↔ model hiển thị (`KeoDoc.toKeo(...)`), định dạng giờ, đếm ngược | Phong Hào |
| `data/remote/FirestoreFlows.kt` | `snapshotFlow()`: listener realtime của Firestore thành `Flow` | Phong Hào |
| `data/repository/KeoRepository.kt` | Interface + 2 hàm đọc đã làm; **các hàm `TODO` là việc của Huy Hoàng** | Huy Hoàng |
| `data/repository/MemberRepository.kt` | Tham gia, xin vào, duyệt, từ chối, rời kèo (transaction) | Phong Hào |
| `data/repository/ChatRepository.kt` | Gửi, nghe realtime, xóa tin | Phong Hào |
| `ui/lobby/LobbyViewModel.kt`, `ChatViewModel.kt`, `ui/mykeo/MyKeoViewModel.kt` | S15, S16, S17 chạy bằng dữ liệu thật | Phong Hào |
| `firestore.rules` | Luật truy cập cho cả 4 collection (bản đầu, mỗi người kiểm tra phần mình) | chung |

`AuthRepository`, `UserRepository`, `AuthViewModel` của Minh Khoa giữ nguyên.

## 2. Dữ liệu chạy thế nào (MVVM)

```
Fragment (chỉ vẽ + báo khi bấm)
   │  collectWhenStarted(viewModel.state) { render(it) }        ▲
   ▼                                                           │ StateFlow<UiState<...>>
ViewModel (ghép dữ liệu, chọn nút, xử lý bấm)  ────────────────┘
   │  gọi interface: keoRepository.observeKeo(id), memberRepository.join(...)
   ▼
Repository (FirestoreXxxRepository: đọc/ghi Firestore, transaction)
   ▼
Firestore  ── listener realtime ──►  Flow phát dữ liệu mới  ──►  ViewModel  ──►  Fragment tự vẽ lại
```

Quy tắc:

- **Fragment không gọi Firestore, không gọi Repository.** Chỉ gọi ViewModel.
- **ViewModel không giữ `Context`, không đụng View.** Chữ cho người dùng trả về dạng `UiText.Res(R.string.xxx)`, Fragment đổi thành chữ bằng `resolve(context)`.
- **Ghi xong không tự sửa danh sách trên màn hình.** Listener Firestore báo về là màn hình tự đổi (cả trên máy người khác).
- Chỉ code trong `data/` mới import `com.google.firebase...` (checklist mục 10): `grep -rl "com.google.firebase" app/src/main/java/com/keonhanh/app/ui` phải không in dòng nào.

## 3. Mẫu một màn hình theo khung

ViewModel:

```kotlin
class ExploreViewModel(private val keoRepository: KeoRepository) : ViewModel() {
    val state: StateFlow<UiState<List<Keo>>> = keoRepository.observeActiveKeos()
        .map { docs -> UiState.Success(docs.map { it.toKeo(members = emptyList(), myUid = null) }) }
        .catch { emit(UiState.Error(it.toUiText())) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
}
```

Fragment:

```kotlin
private val viewModel by appViewModels { container -> ExploreViewModel(container.keoRepository) }

override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    ...
    collectWhenStarted(viewModel.state) { state ->
        when (state) {
            UiState.Loading -> { /* hiện vòng tải */ }
            is UiState.Success -> adapter.submitList(state.data)
            is UiState.Error -> Snackbar.make(binding.root, state.message.resolve(requireContext()), Snackbar.LENGTH_SHORT).show()
        }
    }
}
```

Thao tác ghi (tạo, sửa, xóa) chạy trong `viewModelScope.launch { try { ... } catch (e: Throwable) { ... e.toUiText() ... } }`.
Xem `LobbyViewModel.runAction` để có mẫu đầy đủ (khóa nút khi đang gửi, báo thành công/lỗi bằng Snackbar).

## 4. Việc của Huy Hoàng

### 4.1. Viết các hàm `TODO` trong `KeoRepository.kt`

Mỗi hàm đã có gợi ý code ngay trong `TODO(...)`. Gọi chưa viết xong thì app không crash mà hiện Snackbar "Phần này đang được làm".

| Hàm | Màn | Ghi chú quan trọng |
| --- | --- | --- |
| `createKeo(draft, hostId, hostName)` | S12 | **Cùng một batch**: tạo `keos/{id}` bằng `draft.toNewKeoMap(...)` (memberCount = 1) **và** `members/{hostId}` bằng `newMemberMap(hostId, hostName, MemberRole.HOST, MemberState.JOINED)`. Thiếu document chủ kèo thì luật truy cập từ chối, và S15 không biết ai là chủ kèo |
| `observeActiveKeos()` | S09, S10 | Kèo có `startAt` còn trong tương lai (trừ 15 phút check-in); `limit(50)` |
| `updateKeo(keoId, draft)` | S12 chế độ sửa | Không ghi `memberCount`, `hostId`, `inviteCode` (luật chặn) |
| `deleteKeo(keoId)` | S15 "Hủy kèo" | Xóa `members/` và `messages/` rồi mới xóa kèo, trong một batch. **Nút Hủy kèo ở S15 đã gọi hàm này** |
| `findByInviteCode(code)` | S07 | `whereEqualTo(KeoFields.INVITE_CODE, code.uppercase())` |

### 4.2. Phần dính tới Phong Hào

- **`memberCount` chỉ được đổi trong `MemberRepository`** (transaction cùng lúc thêm/xóa thành viên). Ở S12, `createKeo` đặt 1; sửa kèo không đụng tới trường này.
- Luật `keos` trong `firestore.rules` đã có bản đầu (tạo, sửa, xóa). Hàm `memberCountStep` là phần Phong Hào viết cho transaction tham gia/rời, **đừng xóa**.
- Đổi `KeoDoc` sang `Keo` để hiện thẻ kèo: `keoDoc.toKeo(members = emptyList(), myUid = uid, distanceKm = khoangCach)`. Số người trên thẻ lấy từ `memberCount` (đã sửa `KeoCardBinder`), không cần tải từng thành viên.
- Khám phá mở S15 bằng `bundleOf(LobbyFragment.ARG_KEO_ID to keo.id)` như cũ, chỉ cần id là id thật trên Firestore. **Còn mở S15 bằng id mẫu (`k1`, `k2`...) thì S15 báo "Kèo không còn tồn tại"**: bình thường, vì S15 đã đọc Firestore.

### 4.3. Thêm vào ERD 3 trường "lưu sẵn"

Để không phải đọc `users/{uid}` cho từng người trong phòng chờ và từng tin nhắn:

- `MEMBER.uid` (lặp lại id document; cần cho truy vấn "Kèo của tôi")
- `MEMBER.displayName`
- `MESSAGE.senderName`

Ghi chú trên ERD: "lưu sẵn lúc vào kèo / lúc gửi tin".

## 5. Cài đặt trên Firebase console (một người làm, cả nhóm dùng chung)

1. **Luật truy cập**: Firestore Database → Rules → dán toàn bộ `firestore.rules` → Publish. Thay chế độ test trước mốc 3.
2. **Index cho S17 Kèo của tôi**: lần đầu mở tab "Kèo của tôi", Logcat báo lỗi `FAILED_PRECONDITION ... requires a COLLECTION_GROUP_ASC index` kèm một link. Bấm link, Create index, chờ 1–2 phút.
   (Hoặc làm tay: Firestore → Indexes → Single field → Add exemption: collection `members`, field `uid`, bật *Collection group* Ascending.)
3. **Tạo kèo thử khi S12 chưa xong** (Firestore → Start collection `keos` → Add document, ghi tay bỏ qua luật):

   | Trường | Kiểu | Ví dụ |
   | --- | --- | --- |
   | hostId | string | uid của một tài khoản đã đăng ký (xem ở Authentication) |
   | sport | string | `SOCCER` / `BADMINTON` / `BOARD_GAME` |
   | title, placeName | string | `Đá bóng 7 người`, `Sân bóng gần cổng trường` |
   | lat, lng | number | `16.0736`, `108.15` |
   | startAt, deadlineAt | timestamp | giờ hẹn tối nay; hạn chốt trước giờ hẹn |
   | createdAt | timestamp | bây giờ |
   | capacity, memberCount | number | `7`, `1` |
   | minTrust | null | (hoặc number `70` để thử "Xin vào") |
   | status, inviteCode | string | `OPEN`, `BD7K2Q` |

   Trong document đó thêm subcollection `members`, document id = hostId, trường: `uid` (string), `displayName` (string), `role` = `HOST`, `state` = `JOINED`, `joinedAt` (timestamp).

## 6. Lưu ý cho Minh Khoa

- `UserRepository.getUserProfile` dùng `document.toObject(UserProfile::class.java)`, nhưng `UserProfile` chưa có giá trị mặc định cho các trường nên Firestore không tạo được object (cần constructor rỗng): hàm luôn trả lỗi. S15 và S16 đã có đường dự phòng (lấy tên từ email, ẩn điểm uy tín chủ kèo), nhưng nên sửa `UserProfile` thành `val uid: String = ""`, `val attended: Int = 0`...
- Tuần 4 ghi check-in: `members/{uid}` đã có sẵn `checkInAt`, `checkInDistanceM`, `attendance`; thêm luật `update` cho chính mình trong `firestore.rules` (đã chừa chỗ).

## 7. Cách thử S15, S16, S17

1. Đăng ký 2 tài khoản, chạy app trên 2 máy ảo, mỗi máy một tài khoản.
2. Tạo một kèo (S12 khi Hoàng xong, hoặc tạo tay theo mục 5.3).
3. Máy B mở kèo đó → **Tham gia kèo** → máy A thấy thành viên mới hiện ngay.
4. Hai máy cùng bấm Tham gia khi chỉ còn 1 chỗ: một máy vào, máy kia báo "Kèo đã đủ người".
5. Chat ở S16 từ 2 máy; nhấn giữ tin của mình để xóa.
6. Máy B **Rời kèo**; tab Kèo của tôi ở máy B mất kèo đó.
7. Kèo có `minTrust`: máy B **Xin vào** → máy A (chủ kèo) menu ⋮ → **Duyệt người xin vào**.

## 8. Giao diện mới cần thêm vào Figma (theo lời Minh Khoa)

Những trạng thái sau là mới so với mockup tuần 2. Phong Hào vẽ thêm vào trang **Mockup** của file `KeoNhanh_Tuan2`, dùng lại component có sẵn:

- S15: nút "Hủy yêu cầu xin vào" (đang chờ duyệt), "Đã đủ người", "Đã chốt · vắng mặt tính là bùng kèo", "Kèo đã kết thúc"; thanh tải; trạng thái lỗi "Không mở được kèo"; thẻ chat "Vào kèo để xem chat phòng chờ".
- S15: hộp thoại "Rời kèo này?", "Hủy kèo?" (S19) và hộp thoại duyệt người xin vào (tạm thay S08).
- S16: hộp thoại "Xóa tin nhắn này?" khi nhấn giữ.
- S17: thanh tải và trạng thái rỗng riêng cho từng tab.

## 9. Mở project trên máy có Android Studio mới

Android Studio bản mới dùng JDK 25, mà Gradle 8.14 của project chưa chạy được trên JDK 25 (báo lỗi chỉ có dòng `25.0.2`).
`app/build.gradle.kts` lại đòi đúng JDK 17 (`jvmToolchain(17)`).
Sửa: Settings → Build, Execution, Deployment → Build Tools → Gradle → **Gradle JDK** → Download JDK → chọn bản **17** → Sync lại.
