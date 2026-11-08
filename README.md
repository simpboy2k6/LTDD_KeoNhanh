# KèoNhanh – project Android, tuần 2 (layout tĩnh)

Ứng dụng ghép kèo thể thao và board game quanh trường. Bản này là **khung app + layout tĩnh** cho 12 màn hình P0 (và S18 để tab Chat mở được), đúng tiêu chí tuần 2: theme thống nhất (màu, font, icon), luồng màn hình bằng Navigation, dữ liệu mẫu, chưa nối Firebase.

## Mở project

1. Android Studio bản mới (Ladybug 2024.2 trở lên) → **File › Open** → chọn thư mục `KeoNhanh`.
2. Chờ Gradle Sync. Nếu Android Studio đề nghị nâng cấp Android Gradle Plugin thì chọn đồng ý.
3. Chạy trên emulator hoặc máy thật Android 8.0 (API 26) trở lên.

Cấu hình: Kotlin 2.0, AGP 8.7, compileSdk/targetSdk 35, minSdk 26, ViewBinding, Navigation component, Material 3 (XML Views).

## Luồng demo (quay video báo cáo tuần)

1. Mở app → **S02 Đăng nhập** → bấm Đăng nhập → **S09 Khám phá – bản đồ**.
2. Chip "3 km" mở **S11 Bộ lọc**; nút "Danh sách" sang **S10** (bấm chip môn để lọc).
3. Icon quét QR ở thanh tìm kiếm → **S07 Quét mã mời**: chạm khung camera để giả lập quét, hoặc gõ mã `MS8X4P` / `BD7K2Q` / `CL3H9T` / `CT5N2W` → mở **S15** của kèo đó.
4. Ở S15 của "Ma Sói tối nay" (mình là thành viên): icon QR trên thanh tiêu đề → **S06 Mã mời** (sao chép mã, chia sẻ link).
5. Tab **Kèo của tôi** → "Đang làm chủ" → "Cầu lông đôi nam nữ": kèo đang trong giờ check-in, có **thẻ check-in theo khoảng cách**. Menu ⋮ → "Đổi trạng thái check-in (demo)" để xem đủ 7 trạng thái; ở trạng thái "đủ gần" bấm **Check-in**.
6. Nút chat → **S16** (gõ và gửi thử); nút "Tạo kèo" ở Khám phá → **S12** → ô Điểm hẹn → **S13 Chọn điểm hẹn** → Tạo kèo.
7. Tab **Chat** (S18), **Hồ sơ** (S05, Đăng xuất quay về S02); tab "Đã xong" ở Kèo của tôi là trạng thái rỗng.

## Cấu trúc thư mục — file nào ở đâu

```
KeoNhanh/
├── settings.gradle.kts            khai báo module :app và kho thư viện
├── build.gradle.kts               plugin cấp project
├── gradle.properties
├── gradle/libs.versions.toml      MỌI phiên bản thư viện (thêm Firebase, osmdroid... ở đây)
├── gradle/wrapper/                Gradle 8.9
├── licenses/BeVietnamPro-OFL.txt  giấy phép font
└── app/
    ├── build.gradle.kts           minSdk, viewBinding, dependencies
    └── src/main/
        ├── AndroidManifest.xml    1 Activity duy nhất
        ├── java/com/keonhanh/app/
        │   ├── MainActivity.kt            khung app: NavHost + bottom nav, xử lý tràn viền
        │   ├── data/
        │   │   ├── model/                 Keo, Sport, KeoStatus, Member, Message, UserProfile,
        │   │   │                          CheckIn (luật 100 m + 7 trạng thái check-in)
        │   │   └── sample/SampleData.kt   dữ liệu mẫu tuần 2 (tuần 3 thay bằng repository/)
        │   ├── ui/                        mỗi nhóm màn hình một package
        │   │   ├── auth/                  S02 LoginFragment
        │   │   ├── profile/               S05 ProfileFragment
        │   │   ├── checkin/               S06 InviteQrFragment, S07 ScanQrFragment, CheckInCard (thẻ check-in)
        │   │   ├── explore/               S09, S10, S11 + ExploreHeader (phần đầu dùng chung)
        │   │   ├── create/                S12 CreateKeoFragment, S13 PickLocationFragment
        │   │   ├── lobby/                 S15 LobbyFragment, S16 LobbyChatFragment, MessageAdapter
        │   │   ├── mykeo/                 S17 MyKeoFragment
        │   │   ├── chat/                  S18 ChatListFragment, ChatRoomAdapter
        │   │   └── common/                KeoAdapter, KeoCardBinder (thẻ kèo), MemberAdapter
        │   └── util/ViewUtils.kt          avatar chữ cái đầu, định dạng km, Snackbar "làm sau"
        └── res/
            ├── layout/                    activity_ · fragment_ · item_ · view_ · bottom_sheet_
            ├── navigation/nav_graph.xml   luồng màn hình (mục 3 tài liệu tuần 2)
            ├── menu/                      bottom_nav_menu (4 tab), menu_lobby
            ├── values/                    colors, dimens, type (8 kiểu chữ), themes, styles,
            │                              strings (chung) + strings_auth / _explore / _lobby
            ├── color/                     màu theo trạng thái (chip, tab, viền ô nhập)
            ├── drawable/                  icon ic_*_24 / ic_*_16, nền bg_*, ảnh giữ chỗ img_*
            ├── drawable-nodpi/            ảnh QR mẫu (PNG)
            ├── font/                      Be Vietnam Pro 400/500/600/700
            └── mipmap-anydpi-v26/         icon launcher
```

Quy ước đặt tên: layout `fragment_<màn>.xml`, `item_<dòng danh sách>.xml`, `view_<khối dùng lại>.xml`; id có tiền tố theo loại (`tv_`, `iv_`, `btn_`, `rv_`, `chip_`, `til_`, `et_`) để ViewBinding sinh tên dễ đọc (`binding.btnLogin`).

## Ai giữ file nào

| Thành viên | File của mình | File dùng chung chỉ người này sửa |
| --- | --- | --- |
| TV1 | `ui/auth`, `ui/profile`, `ui/checkin`, `data/model/CheckIn.kt`, `fragment_login/profile/invite_qr/scan_qr.xml`, `view_checkin_card.xml` (nhúng vào S15), `strings_auth.xml` | |
| TV2 | `ui/explore`, `ui/create`, `fragment_explore_*.xml`, `bottom_sheet_filter.xml`, `fragment_create_keo.xml`, `fragment_pick_location.xml`, `view_explore_header.xml`, `strings_explore.xml`, icon `ic_*` | |
| TV3 | `ui/lobby`, `ui/mykeo`, `ui/chat`, `fragment_lobby*.xml`, `fragment_my_keo.xml`, `fragment_chat_list.xml`, `strings_lobby.xml` | `colors.xml`, `dimens.xml`, `type.xml`, `themes.xml`, `styles.xml`, `strings.xml`, `nav_graph.xml`, `item_keo.xml`, `item_member.xml`, `view_empty_state.xml`, `MainActivity.kt` |

## Quy tắc layout (kiểm tra trước khi tạo pull request)

Ba lệnh sau phải **không in ra dòng nào**:

```bash
grep -rnE '="#[0-9A-Fa-f]{3,8}"' app/src/main/res/layout
grep -rn 'android:textSize=' app/src/main/res/layout
grep -rnE 'android:(text|hint)="[^@]' app/src/main/res/layout
```

## Thay đổi so với bản đầu (theo góp ý của thầy)

- Mã QR **không dùng để check-in** nữa mà dùng để **mời vào kèo**: S06 là Mã mời (QR cố định + mã 6 ký tự + chia sẻ link), S07 là Quét mã mời (mở từ Khám phá).
- **Check-in theo khoảng cách** tới điểm hẹn, làm ngay trong S15 bằng thẻ `view_checkin_card.xml`. Luật tạm chốt trong `data/model/CheckIn.kt`: trong 100 m, sai số GPS dưới 50 m, giờ hẹn ± 15 phút.
- **S13 Chọn điểm hẹn** lên P0 vì tọa độ điểm hẹn quyết định check-in.
- Người vào bằng mã mời vẫn theo ngưỡng uy tín (kèo có ngưỡng thì vẫn "Xin vào"); thành viên nào cũng mời được.

## Việc của tuần sau (đã ghi chú trong code)

- Thêm Firebase (Auth, Firestore) vào `libs.versions.toml`, đặt `google-services.json` vào `app/` (đã có trong `.gitignore`).
- `data/sample/SampleData.kt` → `data/repository/` đọc Firestore; Fragment giữ nguyên layout.
- S09, S13: thay `iv_map` bằng `org.osmdroid.views.MapView`; S13 trả về tọa độ thật.
- S15: nút chính đổi theo đủ 5 trạng thái kèo; đồng hồ đếm ngược chạy thật.
- Tuần 4: vị trí bằng FusedLocationProviderClient + `Location.distanceBetween` để chọn trạng thái thẻ check-in; S06 sinh QR bằng ZXing; S07 quét bằng Code Scanner; deep link `keonhanh://moi/{code}`.

Icon: Material Symbols Rounded (Apache 2.0). Font: Be Vietnam Pro (SIL OFL 1.1).
