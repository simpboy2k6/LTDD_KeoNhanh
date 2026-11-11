package com.keonhanh.app.data.repository

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.keonhanh.app.data.remote.FirestorePaths
import com.keonhanh.app.data.remote.KeoDoc
import com.keonhanh.app.data.remote.KeoDraft
import com.keonhanh.app.data.remote.snapshotFlow
import com.keonhanh.app.data.remote.toKeoDoc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Dữ liệu kèo: keos/{keoId}. File này của Huy Hoàng.
 *
 * Phong Hào đã viết sẵn interface (để cả nhóm code theo cùng một hợp đồng) và 2 hàm đọc mà S15, S17 cần.
 * Các hàm còn `TODO(...)` là phần của Hoàng; mỗi hàm có gợi ý cách làm ngay bên trong.
 * Đọc thêm: docs/HUONG_DAN_TUAN3_MVVM.md, mục "Việc của Huy Hoàng".
 */
interface KeoRepository {

    /** S15: một kèo, phát lại mỗi khi document đổi (có người vào/ra, chủ kèo sửa); phát null nếu kèo bị xóa. Đã làm. */
    fun observeKeo(keoId: String): Flow<KeoDoc?>

    /** S17: đọc một lần nhiều kèo theo id; id không còn tồn tại thì bỏ qua. Đã làm. */
    suspend fun getKeos(keoIds: List<String>): List<KeoDoc>

    /** S09, S10: các kèo chưa kết thúc để hiện ở Khám phá. TODO Huy Hoàng. */
    fun observeActiveKeos(): Flow<List<KeoDoc>>

    /** S12: tạo kèo và document chủ kèo, trả về keoId mới. TODO Huy Hoàng. */
    suspend fun createKeo(draft: KeoDraft, hostId: String, hostName: String): String

    /** S12 ở chế độ sửa: chủ kèo đổi tên, giờ, số người... TODO Huy Hoàng. */
    suspend fun updateKeo(keoId: String, draft: KeoDraft)

    /** S15 "Hủy kèo" (chỉ chủ kèo): xóa thành viên, tin nhắn rồi xóa kèo. TODO Huy Hoàng. */
    suspend fun deleteKeo(keoId: String)

    /** S07: tìm kèo theo mã mời 6 ký tự; null nếu không có. TODO Huy Hoàng (Minh Khoa gọi ở tuần 4). */
    suspend fun findByInviteCode(code: String): KeoDoc?
}

class FirestoreKeoRepository(
    private val firestore: FirebaseFirestore,
) : KeoRepository {

    private val keos get() = firestore.collection(FirestorePaths.KEOS)

    override fun observeKeo(keoId: String): Flow<KeoDoc?> =
        keos.document(keoId).snapshotFlow().map { it.toKeoDoc() }

    override suspend fun getKeos(keoIds: List<String>): List<KeoDoc> =
        // whereIn nhận tối đa 30 giá trị mỗi truy vấn nên chia nhỏ danh sách
        keoIds.distinct().chunked(30).flatMap { chunk ->
            keos.whereIn(FieldPath.documentId(), chunk).get().await()
                .documents.mapNotNull { it.toKeoDoc() }
        }

    override fun observeActiveKeos(): Flow<List<KeoDoc>> = TODO(
        """
        Huy Hoàng: truy vấn các kèo chưa kết thúc rồi nghe realtime, ví dụ:
            val from = Timestamp(Date(System.currentTimeMillis() - CheckInRules.WINDOW_MINUTES * 60_000L))
            return keos.whereGreaterThan(KeoFields.START_AT, from)
                .orderBy(KeoFields.START_AT)
                .limit(50)                       // gói Spark: giới hạn lượt đọc
                .snapshotFlow()
                .map { snapshot -> snapshot.documents.mapNotNull { it.toKeoDoc() } }
        Khoảng cách và lọc theo môn, bán kính làm trong ExploreViewModel (tính Haversine trên máy).
        ExploreViewModel đổi KeoDoc sang Keo bằng keoDoc.toKeo(members = emptyList(), myUid, distanceKm = ...).
        """,
    )

    override suspend fun createKeo(draft: KeoDraft, hostId: String, hostName: String): String = TODO(
        """
        Huy Hoàng: tạo kèo + document chủ kèo trong CÙNG MỘT batch, ví dụ:
            val keoRef = keos.document()                         // Firestore tự sinh keoId
            val code = taoMaMoiKhongTrung()                      // 6 ký tự, kiểm tra bằng whereEqualTo(KeoFields.INVITE_CODE, code)
            firestore.batch()
                .set(keoRef, draft.toNewKeoMap(hostId, code))    // memberCount = 1 (chủ kèo)
                .set(
                    keoRef.collection(FirestorePaths.MEMBERS).document(hostId),
                    newMemberMap(hostId, hostName, MemberRole.HOST, MemberState.JOINED),
                )
                .commit().await()
            return keoRef.id
        Bắt buộc có document members/{hostId} với role = HOST:
        - S15 dựa vào nó để biết ai là chủ kèo và hiện nút "Hủy kèo";
        - luật truy cập (firestore.rules) chỉ cho tạo kèo khi document này được tạo cùng lúc.
        """,
    )

    override suspend fun updateKeo(keoId: String, draft: KeoDraft) = TODO(
        """
        Huy Hoàng: keos.document(keoId).update(mapOf(...các trường của draft..., KeoFields.UPDATED_AT to FieldValue.serverTimestamp())).await()
        KHÔNG ghi memberCount, hostId, inviteCode, createdAt ở đây: luật truy cập chặn đổi các trường đó khi sửa kèo.
        Nếu cho giảm capacity, kiểm tra capacity >= memberCount trước khi lưu.
        """,
    )

    override suspend fun deleteKeo(keoId: String) = TODO(
        """
        Huy Hoàng: xóa document kèo KHÔNG tự xóa members/ và messages/ bên dưới (rủi ro mục 11 kế hoạch). Ví dụ:
            val keoRef = keos.document(keoId)
            val members = keoRef.collection(FirestorePaths.MEMBERS).get().await()
            val messages = keoRef.collection(FirestorePaths.MESSAGES).get().await()
            val batch = firestore.batch()
            members.documents.forEach { batch.delete(it.reference) }
            messages.documents.forEach { batch.delete(it.reference) }
            batch.delete(keoRef)                                  // xóa kèo sau cùng
            batch.commit().await()
        Một batch tối đa 500 thao tác, đủ cho kèo ít người. Luật truy cập cho chủ kèo xóa mọi thành viên và tin nhắn.
        Phong Hào đã gọi hàm này ở nút "Hủy kèo" của S15 (LobbyViewModel.cancelKeo).
        """,
    )

    override suspend fun findByInviteCode(code: String): KeoDoc? = TODO(
        """
        Huy Hoàng: keos.whereEqualTo(KeoFields.INVITE_CODE, code.trim().uppercase()).limit(1).get().await()
                       .documents.firstOrNull()?.toKeoDoc()
        """,
    )
}
