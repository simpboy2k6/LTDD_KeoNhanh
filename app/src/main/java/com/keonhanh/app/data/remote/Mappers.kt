package com.keonhanh.app.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.keonhanh.app.data.model.CheckInRules
import com.keonhanh.app.data.model.Keo
import com.keonhanh.app.data.model.Member
import com.keonhanh.app.data.model.Message
import com.keonhanh.app.data.model.MyRole
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * Chuyển đổi giữa 3 dạng dữ liệu:
 *   DocumentSnapshot (Firestore)  ->  ...Doc (FirestoreDocs.kt)  ->  model hiển thị (Keo, Member, Message)
 *   ...Doc  ->  Map<String, Any?> để ghi lên Firestore
 *
 * Đọc từng trường bằng getString/getLong thay vì document.toObject(...):
 * toObject cần lớp có constructor rỗng và thuộc tính var, sai một chỗ là trả về giá trị mặc định mà không báo lỗi.
 */

// ---------- Firestore -> Doc ----------

/** null nếu document không tồn tại (kèo đã bị xóa, hoặc id sai). */
fun DocumentSnapshot.toKeoDoc(): KeoDoc? {
    if (!exists()) return null
    return KeoDoc(
        id = id,
        hostId = getString(KeoFields.HOST_ID).orEmpty(),
        sport = getString(KeoFields.SPORT).orEmpty(),
        title = getString(KeoFields.TITLE).orEmpty(),
        placeName = getString(KeoFields.PLACE_NAME).orEmpty(),
        lat = getDouble(KeoFields.LAT) ?: 0.0,
        lng = getDouble(KeoFields.LNG) ?: 0.0,
        startAt = getTimestamp(KeoFields.START_AT) ?: Timestamp.now(),
        deadlineAt = getTimestamp(KeoFields.DEADLINE_AT) ?: Timestamp.now(),
        capacity = getLong(KeoFields.CAPACITY)?.toInt() ?: 0,
        memberCount = getLong(KeoFields.MEMBER_COUNT)?.toInt() ?: 0,
        minTrust = getLong(KeoFields.MIN_TRUST)?.toInt(),
        status = getString(KeoFields.STATUS).orEmpty(),
        inviteCode = getString(KeoFields.INVITE_CODE).orEmpty(),
        createdAt = getTimestamp(KeoFields.CREATED_AT),
        updatedAt = getTimestamp(KeoFields.UPDATED_AT),
    )
}

fun DocumentSnapshot.toMemberDoc(): MemberDoc = MemberDoc(
    uid = getString(MemberFields.UID) ?: id,
    displayName = getString(MemberFields.DISPLAY_NAME).orEmpty(),
    role = enumOrDefault(getString(MemberFields.ROLE), MemberRole.MEMBER),
    state = enumOrDefault(getString(MemberFields.STATE), MemberState.JOINED),
    joinedAt = getTimestamp(MemberFields.JOINED_AT, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
    checkInAt = getTimestamp(MemberFields.CHECK_IN_AT),
    checkInDistanceM = getLong(MemberFields.CHECK_IN_DISTANCE_M)?.toInt(),
    attendance = getString(MemberFields.ATTENDANCE)?.let { enumOrDefault(it, Attendance.ABSENT) },
)

fun DocumentSnapshot.toMessageDoc(): MessageDoc = MessageDoc(
    id = id,
    senderId = getString(MessageFields.SENDER_ID).orEmpty(),
    senderName = getString(MessageFields.SENDER_NAME).orEmpty(),
    text = getString(MessageFields.TEXT).orEmpty(),
    // ESTIMATE: tin vừa gửi hiện giờ ước tính ngay, không phải chờ server trả giờ thật
    sentAt = getTimestamp(MessageFields.SENT_AT, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
)

private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

// ---------- Doc -> Firestore (để ghi) ----------

/**
 * Map để tạo document kèo mới (Huy Hoàng dùng trong createKeo).
 * memberCount = 1 vì chủ kèo được tính là thành viên đầu tiên (document members/{hostId} tạo cùng batch).
 */
fun KeoDraft.toNewKeoMap(hostId: String, inviteCode: String): Map<String, Any?> = mapOf(
    KeoFields.HOST_ID to hostId,
    KeoFields.SPORT to sport.name,
    KeoFields.TITLE to title,
    KeoFields.PLACE_NAME to placeName,
    KeoFields.LAT to lat,
    KeoFields.LNG to lng,
    KeoFields.START_AT to startAt,
    KeoFields.DEADLINE_AT to deadlineAt,
    KeoFields.CAPACITY to capacity,
    KeoFields.MEMBER_COUNT to 1,
    // Ghi null tường minh: luật truy cập kiểm tra `minTrust == null` nên trường phải có mặt
    KeoFields.MIN_TRUST to minTrust,
    KeoFields.STATUS to "OPEN",
    KeoFields.INVITE_CODE to inviteCode,
    KeoFields.CREATED_AT to FieldValue.serverTimestamp(),
    KeoFields.UPDATED_AT to FieldValue.serverTimestamp(),
)

/** Map để tạo document thành viên; joinedAt lấy giờ server để mọi máy thấy cùng một thứ tự. */
fun newMemberMap(uid: String, displayName: String, role: MemberRole, state: MemberState): Map<String, Any?> = mapOf(
    MemberFields.UID to uid,
    MemberFields.DISPLAY_NAME to displayName,
    MemberFields.ROLE to role.name,
    MemberFields.STATE to state.name,
    MemberFields.JOINED_AT to FieldValue.serverTimestamp(),
)

// ---------- Doc -> model hiển thị ----------

/**
 * Ghép một kèo với danh sách thành viên thành model [Keo] mà layout tuần 2 đang dùng
 * (item_keo.xml, fragment_lobby.xml...), nên các Adapter và Binder cũ không phải sửa.
 *
 * @param members thành viên của kèo (cả JOINED và REQUESTED); chỉ JOINED được hiện trong lưới
 * @param myUid uid người đang dùng app, để biết mình là chủ kèo, thành viên hay người ngoài
 * @param hostTrust điểm uy tín của chủ kèo nếu đã đọc được hồ sơ; null thì để 0 (màn hình tự ẩn)
 * @param distanceKm khoảng cách tới điểm hẹn; S09/S10 (Huy Hoàng) tính bằng Haversine rồi truyền vào,
 *   S15/S17 không đo nên để null (thẻ kèo khi đó chỉ hiện tên điểm hẹn)
 */
fun KeoDoc.toKeo(
    members: List<MemberDoc>,
    myUid: String?,
    hostTrust: Int? = null,
    distanceKm: Double? = null,
    nowMillis: Long = System.currentTimeMillis(),
): Keo {
    val joined = members
        .filter { it.state == MemberState.JOINED }
        // Chủ kèo đứng đầu, sau đó theo thứ tự vào kèo
        .sortedWith(compareBy<MemberDoc>({ it.role != MemberRole.HOST }, { it.joinedAt?.seconds ?: Long.MAX_VALUE }))
    val me = members.firstOrNull { it.uid == myUid }
    val myRole = when {
        me == null || me.state != MemberState.JOINED -> MyRole.NONE
        me.role == MemberRole.HOST -> MyRole.HOST
        else -> MyRole.MEMBER
    }
    return Keo(
        id = id,
        title = title,
        sport = sportEnum,
        place = placeName,
        distanceKm = distanceKm,
        time = formatStartTime(startAt),
        deadlineCountdown = formatCountdown(deadlineAt.toDate().time - nowMillis),
        hostName = members.firstOrNull { it.role == MemberRole.HOST }?.displayName.orEmpty(),
        hostTrust = hostTrust ?: 0,
        members = joined.map { Member(id = it.uid, name = it.displayName, checkedIn = it.checkInAt != null) },
        maxSlots = capacity,
        minTrust = minTrust,
        inviteCode = inviteCode,
        status = effectiveStatus(nowMillis),
        checkInWindow = formatCheckInWindow(startAt),
        myRole = myRole,
        memberCount = memberCount,
    )
}

/** Tin nhắn hiển thị ở S16; isMine quyết định bong bóng phải (của mình) hay trái. */
fun MessageDoc.toMessage(myUid: String?): Message = Message(
    id = id,
    sender = Member(id = senderId, name = senderName),
    text = text,
    time = sentAt?.let { formatClock(it) }.orEmpty(),
    isMine = senderId == myUid,
)

// ---------- Định dạng thời gian ----------

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dateTimeFormat = DateTimeFormatter.ofPattern("HH:mm · dd/MM")

private fun Timestamp.toLocal() = Instant.ofEpochMilli(toDate().time).atZone(ZoneId.systemDefault())

fun formatClock(time: Timestamp): String = clockFormat.format(time.toLocal())

/** "19:30 · 11/11" */
fun formatStartTime(time: Timestamp): String = dateTimeFormat.format(time.toLocal())

/** Khung check-in "16:45 – 17:15" = giờ hẹn ± CheckInRules.WINDOW_MINUTES. */
fun formatCheckInWindow(startAt: Timestamp): String {
    val start = startAt.toLocal()
    val minutes = CheckInRules.WINDOW_MINUTES.toLong()
    return "${clockFormat.format(start.minusMinutes(minutes))} – ${clockFormat.format(start.plusMinutes(minutes))}"
}

/** Đếm ngược tới hạn chốt: "1:58:00" khi còn trên 1 giờ, "24:10" khi dưới 1 giờ, hết giờ thì "00:00". */
fun formatCountdown(remainingMillis: Long): String {
    val totalSeconds = (remainingMillis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
