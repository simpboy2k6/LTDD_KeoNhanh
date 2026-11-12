package com.keonhanh.app.emulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import com.keonhanh.app.data.remote.FirestorePaths
import com.keonhanh.app.data.remote.KeoFields
import com.keonhanh.app.data.remote.MemberFields
import com.keonhanh.app.data.remote.MemberRole
import com.keonhanh.app.data.remote.MemberState
import com.keonhanh.app.data.remote.MessageFields
import com.keonhanh.app.data.remote.newMemberMap
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Luật truy cập trong firestore.rules: thử ghi/đọc "bậy" thẳng vào Firestore (bỏ qua Repository)
 * như một người cố tình phá, và kiểm tra luật chặn được.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreRulesEmulatorTest {

    private lateinit var host: TestUser
    private lateinit var b: TestUser
    private lateinit var c: TestUser

    @Before
    fun signIn() = runBlocking {
        host = FirebaseEmulator.signInNew("host", "Minh")
        b = FirebaseEmulator.signInNew("b", "Binh")
        c = FirebaseEmulator.signInNew("c", "Chi")
    }

    // ---------- keos ----------

    @Test
    fun chuaDangNhap_khongDocDuocGi() = runBlocking {
        val keoId = host.createKeo()
        val guest = FirebaseEmulator.signedOut("khach")

        assertDenied("Khách đọc kèo") { guest.keoRef(keoId).get(Source.SERVER).await() }
        assertDenied("Khách đọc thành viên") { guest.keoRef(keoId).collection(FirestorePaths.MEMBERS).get(Source.SERVER).await() }
    }

    @Test
    fun nguoiKhac_khongSuaKhongXoaDuocKeo() = runBlocking {
        val keoId = host.createKeo()
        b.members.join(keoId, b.uid, b.displayName)

        assertDenied("Thành viên sửa tên kèo") { b.keoRef(keoId).update(KeoFields.TITLE, "Kèo của Bình").await() }
        assertDenied("Thành viên xóa kèo") { b.keoRef(keoId).delete().await() }
        assertDenied("Người ngoài xóa kèo") { c.keoRef(keoId).delete().await() }
    }

    @Test
    fun chuKeo_suaDuocNoiDung_nhungKhongSuaMemberCount() = runBlocking {
        val keoId = host.createKeo()

        host.keoRef(keoId).update(KeoFields.TITLE, "Đổi tên kèo", KeoFields.CAPACITY, 6).await()
        assertEquals("Đổi tên kèo", host.serverGet(host.keoRef(keoId)).getString(KeoFields.TITLE))

        assertDenied("Chủ kèo đổi tên kèm memberCount") {
            host.keoRef(keoId).update(KeoFields.TITLE, "x", KeoFields.MEMBER_COUNT, 5).await()
        }
        assertDenied("Chủ kèo đổi mã mời") { host.keoRef(keoId).update(KeoFields.INVITE_CODE, "HACKED").await() }
        assertDenied("Chủ kèo đổi hostId") { host.keoRef(keoId).update(KeoFields.HOST_ID, b.uid).await() }
    }

    @Test
    fun taoKeo_phaiLaChuKeo_vaCoDongChuKeo() = runBlocking {
        val ref = b.firestore.collection(FirestorePaths.KEOS).document()
        val data = mapOf(
            KeoFields.HOST_ID to b.uid, KeoFields.TITLE to "Kèo thiếu chủ", KeoFields.MEMBER_COUNT to 1,
            KeoFields.CAPACITY to 4, KeoFields.MIN_TRUST to null, KeoFields.DEADLINE_AT to minutesFromNow(60),
        )

        assertDenied("Tạo kèo mà không tạo document chủ kèo cùng lúc") { ref.set(data).await() }
        assertDenied("Tạo kèo đứng tên người khác") {
            b.firestore.batch()
                .set(ref, data + (KeoFields.HOST_ID to host.uid))
                .set(ref.collection(FirestorePaths.MEMBERS).document(b.uid), newMemberMap(b.uid, "Binh", MemberRole.HOST, MemberState.JOINED))
                .commit().await()
        }
        assertDenied("Tạo kèo với memberCount sai") {
            b.firestore.batch()
                .set(ref, data + (KeoFields.MEMBER_COUNT to 3))
                .set(ref.collection(FirestorePaths.MEMBERS).document(b.uid), newMemberMap(b.uid, "Binh", MemberRole.HOST, MemberState.JOINED))
                .commit().await()
        }
    }

    // ---------- memberCount và members ----------

    @Test
    fun khongAiTuSuaDuocMemberCount() = runBlocking {
        val keoId = host.createKeo()

        assertDenied("Người ngoài đặt memberCount = 99") { b.keoRef(keoId).update(KeoFields.MEMBER_COUNT, 99).await() }
        assertDenied("Người ngoài tăng memberCount mà không vào kèo") {
            b.keoRef(keoId).update(KeoFields.MEMBER_COUNT, FieldValue.increment(1)).await()
        }
        b.members.join(keoId, b.uid, b.displayName)
        assertDenied("Thành viên tăng memberCount lần nữa") {
            b.keoRef(keoId).update(KeoFields.MEMBER_COUNT, FieldValue.increment(1)).await()
        }
        assertDenied("Thành viên giảm memberCount mà không rời kèo") {
            b.keoRef(keoId).update(KeoFields.MEMBER_COUNT, FieldValue.increment(-1)).await()
        }
        assertEquals(2, host.memberCount(keoId))
    }

    @Test
    fun khongVaoKeoDuocNeuKhongTangSoNguoi_hoacVaoThayNguoiKhac() = runBlocking {
        val keoId = host.createKeo()

        assertDenied("Tạo dòng JOINED mà không tăng memberCount") {
            b.memberRef(keoId, b.uid).set(newMemberMap(b.uid, "Binh", MemberRole.MEMBER, MemberState.JOINED)).await()
        }
        assertDenied("Tạo dòng thành viên cho người khác") {
            b.memberRef(keoId, c.uid).set(newMemberMap(c.uid, "Chi", MemberRole.MEMBER, MemberState.REQUESTED)).await()
        }
        assertDenied("Tự phong làm chủ kèo") {
            b.memberRef(keoId, b.uid).set(newMemberMap(b.uid, "Binh", MemberRole.HOST, MemberState.JOINED)).await()
        }
        assertFalse(host.serverGet(host.memberRef(keoId, b.uid)).exists())
    }

    @Test
    fun keoDay_khongVuotDuocCapacity_duGhiThang() = runBlocking {
        val keoId = host.createKeo(capacity = 2)
        b.members.join(keoId, b.uid, b.displayName)

        assertDenied("Chen vào kèo đã đầy bằng batch tự viết") {
            c.firestore.batch()
                .set(c.memberRef(keoId, c.uid), newMemberMap(c.uid, "Chi", MemberRole.MEMBER, MemberState.JOINED))
                .update(c.keoRef(keoId), KeoFields.MEMBER_COUNT, FieldValue.increment(1))
                .commit().await()
        }
        assertEquals(2, host.memberCount(keoId))
    }

    @Test
    fun keoCoNguong_khongVaoThangDuoc_khongTuDuyetDuoc() = runBlocking {
        val keoId = host.createKeo(minTrust = 70)

        assertDenied("Vào thẳng kèo có ngưỡng uy tín") {
            b.firestore.batch()
                .set(b.memberRef(keoId, b.uid), newMemberMap(b.uid, "Binh", MemberRole.MEMBER, MemberState.JOINED))
                .update(b.keoRef(keoId), KeoFields.MEMBER_COUNT, FieldValue.increment(1))
                .commit().await()
        }

        b.members.requestJoin(keoId, b.uid, b.displayName)
        assertDenied("Tự duyệt yêu cầu của mình") {
            b.firestore.batch()
                .update(b.memberRef(keoId, b.uid), MemberFields.STATE, "JOINED")
                .update(b.keoRef(keoId), KeoFields.MEMBER_COUNT, FieldValue.increment(1))
                .commit().await()
        }
        assertDenied("Thành viên khác duyệt hộ") { c.members.approve(keoId, b.uid) }
        assertEquals(1, host.memberCount(keoId))
    }

    @Test
    fun khongDuoiDuocNguoiKhac_sauHanChotKhongRoiDuoc() = runBlocking {
        val keoId = host.createKeo()
        b.members.join(keoId, b.uid, b.displayName)
        c.members.join(keoId, c.uid, c.displayName)

        assertDenied("Thành viên xóa thành viên khác") { b.memberRef(keoId, c.uid).delete().await() }
        assertDenied("Rời kèo mà không giảm memberCount") { b.memberRef(keoId, b.uid).delete().await() }

        host.moveDeadlineToPast(keoId)
        assertDenied("Rời kèo sau hạn chốt bằng batch tự viết") {
            b.firestore.batch()
                .delete(b.memberRef(keoId, b.uid))
                .update(b.keoRef(keoId), KeoFields.MEMBER_COUNT, FieldValue.increment(-1))
                .commit().await()
        }
        assertEquals(3, host.memberCount(keoId))
    }

    // ---------- messages ----------

    @Test
    fun nguoiNgoai_khongDocKhongGuiDuocChat() = runBlocking {
        val keoId = host.createKeo(minTrust = 70)
        host.chat.send(keoId, host.uid, host.displayName, "Chào cả phòng")
        b.members.requestJoin(keoId, b.uid, b.displayName)

        assertDenied("Người ngoài đọc chat") { c.messages(keoId).get(Source.SERVER).await() }
        assertDenied("Người ngoài gửi tin") { c.chat.send(keoId, c.uid, c.displayName, "Cho mình vào với") }
        assertDenied("Người đang xin vào (chưa duyệt) đọc chat") { b.messages(keoId).get(Source.SERVER).await() }
        assertDenied("Người đang xin vào gửi tin") { b.chat.send(keoId, b.uid, b.displayName, "Duyệt mình đi") }
    }

    @Test
    fun khongMaoDanh_khongSua_khongXoaTinNguoiKhac() = runBlocking {
        val keoId = host.createKeo()
        b.members.join(keoId, b.uid, b.displayName)
        host.chat.send(keoId, host.uid, host.displayName, "Tin của chủ kèo")
        val hostMessage = b.messages(keoId).get(Source.SERVER).await().documents.single()

        assertDenied("Gửi tin mạo danh người khác") { b.chat.send(keoId, host.uid, "Minh", "Mình là chủ kèo đây") }
        assertDenied("Sửa tin đã gửi") { hostMessage.reference.update(MessageFields.TEXT, "đã bị sửa").await() }
        assertDenied("Thành viên xóa tin của người khác") { hostMessage.reference.delete().await() }
        assertDenied("Gửi tin rỗng thẳng vào Firestore") {
            b.messages(keoId).add(
                mapOf(MessageFields.TEXT to "", MessageFields.SENDER_ID to b.uid, MessageFields.SENT_AT to FieldValue.serverTimestamp()),
            ).await()
        }
        assertDenied("Gửi tin với giờ tự đặt (không phải giờ server)") {
            b.messages(keoId).add(
                mapOf(MessageFields.TEXT to "tin từ quá khứ", MessageFields.SENDER_ID to b.uid, MessageFields.SENT_AT to minutesFromNow(-600)),
            ).await()
        }
    }

    // ---------- hủy kèo (gợi ý cho KeoRepository.deleteKeo của Huy Hoàng) ----------

    @Test
    fun chuKeoHuyKeo_xoaThanhVienTinNhanVaKeoTrongMotBatch() = runBlocking {
        val keoId = host.createKeo()
        b.members.join(keoId, b.uid, b.displayName)
        b.chat.send(keoId, b.uid, b.displayName, "Tin của thành viên")
        host.chat.send(keoId, host.uid, host.displayName, "Tin của chủ kèo")

        val keoRef = host.keoRef(keoId)
        val members = keoRef.collection(FirestorePaths.MEMBERS).get(Source.SERVER).await()
        val messages = keoRef.collection(FirestorePaths.MESSAGES).get(Source.SERVER).await()
        val batch = host.firestore.batch()
        members.documents.forEach { batch.delete(it.reference) }
        messages.documents.forEach { batch.delete(it.reference) }
        batch.delete(keoRef)
        batch.commit().await()

        assertFalse(b.serverGet(b.keoRef(keoId)).exists())
        assertEquals(0, b.keoRef(keoId).collection(FirestorePaths.MEMBERS).get(Source.SERVER).await().size())
    }
}
