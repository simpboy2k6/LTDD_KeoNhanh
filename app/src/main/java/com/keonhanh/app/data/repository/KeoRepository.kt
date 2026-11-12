package com.keonhanh.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.keonhanh.app.data.remote.FirestorePaths
import com.keonhanh.app.data.remote.KeoDoc
import com.keonhanh.app.data.remote.KeoDraft
import com.keonhanh.app.data.remote.snapshotFlow
import com.keonhanh.app.data.remote.toKeoDoc
import com.keonhanh.app.data.remote.toNewKeoMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import kotlin.random.Random

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

    override fun observeActiveKeos(): Flow<List<KeoDoc>> {
        val from = Timestamp(Date(System.currentTimeMillis() - 15 * 60_000L))
        return keos.whereGreaterThan("startAt", from)
            .orderBy("startAt")
            .limit(50)
            .snapshotFlow()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toKeoDoc() } }
    }

    override suspend fun createKeo(draft: KeoDraft, hostId: String, hostName: String): String {
        val keoRef = keos.document()
        val code = taoMaMoiKhongTrung()

        val batch = firestore.batch()
        batch.set(keoRef, draft.toNewKeoMap(hostId, code))

        val hostMemberRef = keoRef.collection(FirestorePaths.MEMBERS).document(hostId)
        val hostMemberMap = mapOf(
            "uid" to hostId,
            "name" to hostName,
            "role" to "HOST",
            "state" to "JOINED",
            "joinedAt" to FieldValue.serverTimestamp()
        )
        batch.set(hostMemberRef, hostMemberMap)

        batch.commit().await()
        return keoRef.id
    }

    override suspend fun updateKeo(keoId: String, draft: KeoDraft) {
        val updateMap = mapOf(
            "title" to draft.title,
            "sport" to draft.sport.name,
            "placeName" to draft.placeName,
            "lat" to draft.lat,
            "lng" to draft.lng,
            "startAt" to draft.startAt,
            "deadlineAt" to draft.deadlineAt,
            "capacity" to draft.capacity,
            "minTrust" to draft.minTrust,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        keos.document(keoId).update(updateMap).await()
    }

    override suspend fun deleteKeo(keoId: String) {
        val keoRef = keos.document(keoId)
        val members = keoRef.collection(FirestorePaths.MEMBERS).get().await()
        val messages = keoRef.collection(FirestorePaths.MESSAGES).get().await()

        val batch = firestore.batch()
        members.documents.forEach { batch.delete(it.reference) }
        messages.documents.forEach { batch.delete(it.reference) }
        batch.delete(keoRef)
        batch.commit().await()
    }

    override suspend fun findByInviteCode(code: String): KeoDoc? {
        return keos.whereEqualTo("inviteCode", code.trim().uppercase())
            .limit(1).get().await()
            .documents.firstOrNull()?.toKeoDoc()
    }

    private suspend fun taoMaMoiKhongTrung(): String {
        val charPool = ('A'..'Z') + ('0'..'9')
        var isUnique = false
        var code = ""
        while (!isUnique) {
            code = (1..6).map { Random.nextInt(0, charPool.size) }.map(charPool::get).joinToString("")
            val check = keos.whereEqualTo("inviteCode", code).limit(1).get().await()
            if (check.isEmpty) isUnique = true
        }
        return code
    }
}