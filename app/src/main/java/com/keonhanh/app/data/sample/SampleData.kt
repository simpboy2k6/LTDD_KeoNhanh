package com.keonhanh.app.data.sample

import com.keonhanh.app.data.model.ChatRoom
import com.keonhanh.app.data.model.CheckInState
import com.keonhanh.app.data.model.Keo
import com.keonhanh.app.data.model.KeoStatus
import com.keonhanh.app.data.model.Member
import com.keonhanh.app.data.model.Message
import com.keonhanh.app.data.model.MyRole
import com.keonhanh.app.data.model.Sport
import com.keonhanh.app.data.model.UserProfile

/**
 * DỮ LIỆU MẪU CHO TUẦN 2 (layout tĩnh). Không có mạng, không có Firebase.
 * Tuần 3 thay bằng Repository đọc Firestore; các Fragment chỉ đổi nguồn dữ liệu, layout giữ nguyên.
 */
object SampleData {

    private val an = Member("u1", "An")
    private val minh = Member("u2", "Minh")
    private val thao = Member("u3", "Thảo")
    private val long = Member("u4", "Long")
    private val hung = Member("u5", "Hùng")
    private val khoa = Member("u6", "Khoa")
    val me = Member("me", "Minh Anh")

    val currentUser = UserProfile(
        name = "Nguyễn Minh Anh",
        email = "minhanh@sv.truong.edu.vn",
        attended = 9,
        committed = 10,
        lateCancels = 0,
        hosted = 3,
        uid = "1"
    )

    /** Địa điểm trả về từ S13 Chọn điểm hẹn ở tuần 2. */
    const val PICKED_PLACE = "Sân bóng gần cổng trường"

    val keos: List<Keo> = listOf(
        // Đang mở, mình chưa vào, có ngưỡng uy tín -> nút "Xin vào"
        Keo(
            id = "k1",
            title = "Đá bóng 7 người",
            sport = Sport.SOCCER,
            place = "Sân bóng gần cổng trường",
            distanceKm = 0.8,
            time = "18:45 hôm nay",
            deadlineCountdown = "24:10",
            hostName = "Long",
            hostTrust = 90,
            members = listOf(an, minh, thao, long, hung),
            maxSlots = 7,
            minTrust = 70,
            inviteCode = "BD7K2Q",
        ),
        // Đang mở, mình là thành viên -> nút "Rời kèo", được mời bạn bằng QR
        Keo(
            id = "k2",
            title = "Ma Sói tối nay",
            sport = Sport.BOARD_GAME,
            place = "Quán board game gần trường",
            distanceKm = 0.6,
            time = "19:30 hôm nay",
            deadlineCountdown = "00:45:12",
            hostName = "Minh",
            hostTrust = 92,
            members = listOf(an, minh, thao, long, hung, me),
            maxSlots = 8,
            minTrust = 70,
            inviteCode = "MS8X4P",
            myRole = MyRole.MEMBER,
        ),
        // Đang trong khung check-in, mình là chủ kèo -> thẻ check-in theo khoảng cách
        Keo(
            id = "k3",
            title = "Cầu lông đôi nam nữ",
            sport = Sport.BADMINTON,
            place = "Nhà thi đấu khu B",
            distanceKm = 1.4,
            time = "17:00 hôm nay",
            deadlineCountdown = "",
            hostName = "Minh Anh",
            hostTrust = 87,
            members = listOf(me, khoa.copy(checkedIn = true), thao.copy(checkedIn = true), an),
            maxSlots = 4,
            minTrust = null,
            inviteCode = "CL3H9T",
            status = KeoStatus.CHECK_IN,
            checkInWindow = "16:45 – 17:15",
            myRole = MyRole.HOST,
        ),
        // Tên dài 2 dòng: dùng để thử layout có bị vỡ chữ không. Cũng là kèo "quét được" ở S07.
        Keo(
            id = "k4",
            title = "Catan cuối tuần, cần thêm 1 người nữa cho đủ bàn",
            sport = Sport.BOARD_GAME,
            place = "Cà phê sách gần ký túc xá",
            distanceKm = 2.7,
            time = "Thứ 7, 14:00",
            deadlineCountdown = "1:58:00",
            hostName = "Khoa",
            hostTrust = 80,
            members = listOf(khoa, an, long),
            maxSlots = 4,
            minTrust = null,
            inviteCode = "CT5N2W",
        ),
    )

    /** Kèo quanh vị trí hiện tại, gần nhất trước. Tuần 3: geohash + Haversine (slide 9). */
    val nearbyKeos: List<Keo> get() = keos.filter { it.status == KeoStatus.OPEN }.sortedBy { it.distanceKm }

    fun findKeo(id: String): Keo = keos.firstOrNull { it.id == id } ?: keos.first()

    /** Tìm kèo theo mã mời gõ tay ở S07 (không phân biệt hoa thường, bỏ khoảng trắng). */
    fun findByInviteCode(code: String): Keo? {
        val normalized = code.trim().uppercase()
        return keos.firstOrNull { it.inviteCode == normalized }
    }

    /** Thứ tự các trạng thái thẻ check-in khi bấm "Đổi trạng thái check-in (demo)" ở S15. */
    fun checkInDemoStates(keo: Keo): List<CheckInState> = listOf(
        CheckInState.NotYet(keo.checkInWindow),
        CheckInState.TooFar(distanceM = 850),
        CheckInState.PoorAccuracy(accuracyM = 120),
        CheckInState.InRange(distanceM = 42, accuracyM = 18),
        CheckInState.Done(time = "16:52", distanceM = 42),
        CheckInState.NoPermission,
        CheckInState.Missed,
    )

    fun messagesFor(keoId: String): List<Message> {
        val keo = findKeo(keoId)
        return listOf(
            Message("m1", an, "Mình mang bộ bài, 19:20 có mặt nhé!", "19:02", isMine = false),
            Message("m2", minh, "Ok, quán còn bàn lớn ở tầng 2", "19:04", isMine = false),
            Message("m3", me, "Mình tới trễ 5 phút được không?", "19:06", isMine = true),
            Message("m4", thao, "Được nha, ${keo.hostName} giữ chỗ rồi", "19:07", isMine = false),
        )
    }

    val chatRooms: List<ChatRoom>
        get() = keos.filter { it.myRole != MyRole.NONE }.map { keo ->
            val last = messagesFor(keo.id).last()
            ChatRoom(keo, "${last.sender.name}: ${last.text}", last.time)
        }
}