package com.keonhanh.app.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import com.keonhanh.app.R
import com.keonhanh.app.data.model.KeoStatus
import com.keonhanh.app.data.remote.FirestorePaths
import com.keonhanh.app.data.remote.KeoDoc
import com.keonhanh.app.data.remote.KeoFields
import com.keonhanh.app.data.remote.MemberDoc
import com.keonhanh.app.data.remote.MemberFields
import com.keonhanh.app.data.remote.MemberRole
import com.keonhanh.app.data.remote.MemberState
import com.keonhanh.app.data.remote.Membership
import com.keonhanh.app.data.remote.newMemberMap
import com.keonhanh.app.data.remote.snapshotFlow
import com.keonhanh.app.data.remote.toKeoDoc
import com.keonhanh.app.data.remote.toMemberDoc
import com.keonhanh.app.ui.common.AppException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Thành viên của kèo: keos/{keoId}/members/{uid}. File này của Phong Hào.
 *
 * Mọi thao tác làm thay đổi số người (tham gia, duyệt, rời) chạy trong một TRANSACTION:
 * đọc kèo + thành viên, kiểm tra luật, rồi ghi document thành viên và memberCount cùng lúc.
 * Nếu hai người bấm "Tham gia" vào chỗ cuối cùng một lúc, Firestore chạy lại transaction của người đến sau;
 * lần chạy lại thấy kèo đã đủ người nên báo lỗi, memberCount không bao giờ vượt capacity.
 */
interface MemberRepository {

    /** S15: thành viên của một kèo (cả JOINED và REQUESTED), cập nhật realtime. */
    fun observeMembers(keoId: String): Flow<List<MemberDoc>>

    /** S17: mọi kèo mình đang ở trong (mỗi dòng kèm keoId), cập nhật realtime. */
    fun observeMyMemberships(uid: String): Flow<List<Membership>>

    /** S15 "Tham gia kèo": kèo không đặt ngưỡng uy tín, còn chỗ, chưa tới hạn chốt. */
    suspend fun join(keoId: String, uid: String, displayName: String)

    /** S15 "Xin vào": kèo có ngưỡng uy tín; tạo yêu cầu chờ chủ kèo duyệt, chưa tính vào số người. */
    suspend fun requestJoin(keoId: String, uid: String, displayName: String)

    /** S08 (chủ kèo): duyệt người xin vào thành thành viên. */
    suspend fun approve(keoId: String, uid: String)

    /** S08 (chủ kèo): từ chối người xin vào. */
    suspend fun reject(keoId: String, uid: String)

    /** S15 "Rời kèo" hoặc "Hủy yêu cầu": chỉ được trước hạn chốt; chủ kèo không rời được (phải hủy kèo). */
    suspend fun leave(keoId: String, uid: String)
}

class FirestoreMemberRepository(
    private val firestore: FirebaseFirestore,
) : MemberRepository {

    private fun keoRef(keoId: String) = firestore.collection(FirestorePaths.KEOS).document(keoId)
    private fun memberRef(keoId: String, uid: String) = keoRef(keoId).collection(FirestorePaths.MEMBERS).document(uid)

    override fun observeMembers(keoId: String): Flow<List<MemberDoc>> =
        keoRef(keoId).collection(FirestorePaths.MEMBERS).snapshotFlow()
            .map { snapshot -> snapshot.documents.map { it.toMemberDoc() } }

    override fun observeMyMemberships(uid: String): Flow<List<Membership>> =
        // collectionGroup: tìm trong mọi keos/*/members cùng lúc. Lần chạy đầu Firestore báo thiếu index
        // kèm một link trong Logcat; bấm link để tạo index (xem docs/HUONG_DAN_TUAN3_MVVM.md).
        firestore.collectionGroup(FirestorePaths.MEMBERS)
            .whereEqualTo(MemberFields.UID, uid)
            .snapshotFlow()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    // Đường dẫn keos/{keoId}/members/{uid}: parent là members, parent.parent là kèo
                    val keoId = doc.reference.parent.parent?.id ?: return@mapNotNull null
                    Membership(keoId, doc.toMemberDoc())
                }
            }

    override suspend fun join(keoId: String, uid: String, displayName: String) {
        firestore.runTransaction { tx ->
            val keo = tx.readKeo(keoId)
            if (tx.get(memberRef(keoId, uid)).exists()) throw AppException(R.string.error_already_member)
            if (keo.minTrust != null) throw AppException(R.string.error_need_request)
            requireJoinable(keo)

            tx.set(memberRef(keoId, uid), newMemberMap(uid, displayName, MemberRole.MEMBER, MemberState.JOINED))
            tx.changeMemberCount(keoId, +1)
            null
        }.await()
    }

    override suspend fun requestJoin(keoId: String, uid: String, displayName: String) {
        firestore.runTransaction { tx ->
            val keo = tx.readKeo(keoId)
            if (tx.get(memberRef(keoId, uid)).exists()) throw AppException(R.string.error_already_member)
            requireJoinable(keo)

            // Yêu cầu xin vào chưa chiếm chỗ: không đổi memberCount
            tx.set(memberRef(keoId, uid), newMemberMap(uid, displayName, MemberRole.MEMBER, MemberState.REQUESTED))
            null
        }.await()
    }

    override suspend fun approve(keoId: String, uid: String) {
        firestore.runTransaction { tx ->
            val keo = tx.readKeo(keoId)
            val member = tx.get(memberRef(keoId, uid))
            if (!member.exists() || member.toMemberDoc().state != MemberState.REQUESTED) {
                throw AppException(R.string.error_request_not_found)
            }
            requireJoinable(keo)

            tx.update(
                memberRef(keoId, uid),
                MemberFields.STATE, MemberState.JOINED.name,
                MemberFields.JOINED_AT, FieldValue.serverTimestamp(),
            )
            tx.changeMemberCount(keoId, +1)
            null
        }.await()
    }

    override suspend fun reject(keoId: String, uid: String) {
        // Người xin vào chưa được tính vào memberCount nên chỉ cần xóa yêu cầu, không cần transaction
        memberRef(keoId, uid).delete().await()
    }

    override suspend fun leave(keoId: String, uid: String) {
        firestore.runTransaction { tx ->
            val keo = tx.readKeo(keoId)
            val snapshot = tx.get(memberRef(keoId, uid))
            if (!snapshot.exists()) throw AppException(R.string.error_not_member)
            val member = snapshot.toMemberDoc()
            if (member.role == MemberRole.HOST) throw AppException(R.string.error_host_cannot_leave)
            // Sau hạn chốt không rời được nữa: vắng mặt sẽ tính là bùng kèo (bảng nút mục 3 tài liệu tuần 2)
            if (keo.effectiveStatus() !in setOf(KeoStatus.OPEN, KeoStatus.FULL)) throw AppException(R.string.error_leave_locked)

            tx.delete(memberRef(keoId, uid))
            if (member.state == MemberState.JOINED) tx.changeMemberCount(keoId, -1)
            null
        }.await()
    }

    // ---------- dùng chung trong các transaction ----------

    private fun Transaction.readKeo(keoId: String): KeoDoc =
        get(keoRef(keoId)).toKeoDoc() ?: throw AppException(R.string.error_keo_not_found)

    /** Chỉ tham gia, xin vào, duyệt được khi kèo còn mở và còn chỗ. */
    private fun requireJoinable(keo: KeoDoc) {
        when (keo.effectiveStatus()) {
            KeoStatus.OPEN -> Unit
            KeoStatus.FULL -> throw AppException(R.string.error_keo_full)
            else -> throw AppException(R.string.error_keo_closed)
        }
    }

    /** memberCount chỉ đổi ở đây, luôn cùng transaction với việc thêm/xóa document thành viên. */
    private fun Transaction.changeMemberCount(keoId: String, delta: Long) {
        update(
            keoRef(keoId),
            KeoFields.MEMBER_COUNT, FieldValue.increment(delta),
            KeoFields.UPDATED_AT, FieldValue.serverTimestamp(),
        )
    }
}
