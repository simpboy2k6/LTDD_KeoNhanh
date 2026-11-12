package com.keonhanh.app.emulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.Source
import com.keonhanh.app.R
import com.keonhanh.app.data.remote.FirestorePaths
import com.keonhanh.app.data.remote.MemberFields
import com.keonhanh.app.data.remote.MemberState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** MemberRepository chạy thật trên Firestore Emulator: tham gia, xin vào, duyệt, rời kèo. */
@RunWith(AndroidJUnit4::class)
class MemberRepositoryEmulatorTest {

    private lateinit var host: TestUser
    private lateinit var b: TestUser
    private lateinit var c: TestUser

    @Before
    fun signIn() = runBlocking {
        host = FirebaseEmulator.signInNew("host", "Minh")
        b = FirebaseEmulator.signInNew("b", "Binh")
        c = FirebaseEmulator.signInNew("c", "Chi")
    }

    @Test
    fun thamGia_themThanhVien_vaTangSoNguoi() = runBlocking {
        val keoId = host.createKeo(capacity = 4)
        assertEquals(1, host.memberCount(keoId))

        b.members.join(keoId, b.uid, b.displayName)

        val member = b.serverGet(b.memberRef(keoId, b.uid))
        assertTrue("Phải có document members/{uid}", member.exists())
        assertEquals("JOINED", member.getString(MemberFields.STATE))
        assertEquals("MEMBER", member.getString(MemberFields.ROLE))
        assertEquals("Binh", member.getString(MemberFields.DISPLAY_NAME))
        assertEquals(2, host.memberCount(keoId))
    }

    @Test
    fun thamGiaHaiLan_baoDaOTrongKeo_soNguoiKhongDoi() = runBlocking {
        val keoId = host.createKeo()
        b.members.join(keoId, b.uid, b.displayName)

        assertAppError(R.string.error_already_member) { b.members.join(keoId, b.uid, b.displayName) }
        assertEquals(2, host.memberCount(keoId))
    }

    @Test
    fun keoDaDay_khongVaoDuoc() = runBlocking {
        val keoId = host.createKeo(capacity = 2)
        b.members.join(keoId, b.uid, b.displayName)

        assertAppError(R.string.error_keo_full) { c.members.join(keoId, c.uid, c.displayName) }
        assertEquals(2, host.memberCount(keoId))
        assertFalse(c.serverGet(c.memberRef(keoId, c.uid)).exists())
    }

    /** Checklist tuần 3: "memberCount đúng, không vượt capacity" khi hai máy bấm cùng lúc. */
    @Test
    fun haiNguoiTranhChoCuoi_chiMotNguoiVao() = runBlocking {
        repeat(3) {
            val keoId = host.createKeo(capacity = 2)

            val results = coroutineScope {
                listOf(b, c).map { user ->
                    async(Dispatchers.IO) { runCatching { user.members.join(keoId, user.uid, user.displayName) } }
                }.awaitAll()
            }

            assertEquals("Đúng một người vào được", 1, results.count { it.isSuccess })
            assertEquals(2, host.memberCount(keoId))
            val joined = host.keoRef(keoId).collection(FirestorePaths.MEMBERS).get(Source.SERVER).await().size()
            assertEquals("Số document thành viên phải khớp memberCount", 2, joined)
        }
    }

    @Test
    fun keoCoNguongUyTin_phaiXinVao_chuKeoDuyet() = runBlocking {
        val keoId = host.createKeo(capacity = 4, minTrust = 70)

        assertAppError(R.string.error_need_request) { b.members.join(keoId, b.uid, b.displayName) }

        b.members.requestJoin(keoId, b.uid, b.displayName)
        assertEquals("REQUESTED", b.serverGet(b.memberRef(keoId, b.uid)).getString(MemberFields.STATE))
        assertEquals("Xin vào chưa chiếm chỗ", 1, host.memberCount(keoId))

        host.members.approve(keoId, b.uid)
        assertEquals("JOINED", host.serverGet(host.memberRef(keoId, b.uid)).getString(MemberFields.STATE))
        assertEquals(2, host.memberCount(keoId))
    }

    @Test
    fun chuKeoTuChoi_xoaYeuCau_soNguoiKhongDoi() = runBlocking {
        val keoId = host.createKeo(minTrust = 70)
        c.members.requestJoin(keoId, c.uid, c.displayName)

        host.members.reject(keoId, c.uid)

        assertFalse(host.serverGet(host.memberRef(keoId, c.uid)).exists())
        assertEquals(1, host.memberCount(keoId))
    }

    @Test
    fun nguoiXinVaoTuHuyYeuCau() = runBlocking {
        val keoId = host.createKeo(minTrust = 70)
        b.members.requestJoin(keoId, b.uid, b.displayName)

        b.members.leave(keoId, b.uid)

        assertFalse(b.serverGet(b.memberRef(keoId, b.uid)).exists())
        assertEquals(1, host.memberCount(keoId))
    }

    @Test
    fun roiKeo_xoaThanhVien_vaGiamSoNguoi() = runBlocking {
        val keoId = host.createKeo()
        b.members.join(keoId, b.uid, b.displayName)

        b.members.leave(keoId, b.uid)

        assertFalse(b.serverGet(b.memberRef(keoId, b.uid)).exists())
        assertEquals(1, host.memberCount(keoId))
        // Rời rồi vào lại được khi kèo còn chỗ
        b.members.join(keoId, b.uid, b.displayName)
        assertEquals(2, host.memberCount(keoId))
    }

    @Test
    fun chuKeoKhongRoiDuoc_nguoiNgoaiKhongRoiDuoc() = runBlocking {
        val keoId = host.createKeo()

        assertAppError(R.string.error_host_cannot_leave) { host.members.leave(keoId, host.uid) }
        assertAppError(R.string.error_not_member) { b.members.leave(keoId, b.uid) }
        assertEquals(1, host.memberCount(keoId))
    }

    @Test
    fun sauHanChot_khongVao_khongRoiDuoc() = runBlocking {
        val keoId = host.createKeo()
        b.members.join(keoId, b.uid, b.displayName)
        host.moveDeadlineToPast(keoId)

        assertAppError(R.string.error_keo_closed) { c.members.join(keoId, c.uid, c.displayName) }
        assertAppError(R.string.error_leave_locked) { b.members.leave(keoId, b.uid) }
        assertEquals(2, host.memberCount(keoId))
    }

    @Test
    fun keoKhongTonTai_baoLoiRoRang() = runBlocking {
        assertAppError(R.string.error_keo_not_found) { b.members.join("khong-co-keo-nay", b.uid, b.displayName) }
    }

    @Test
    fun nghe_thanhVienCuaMotKeo_capNhatRealtime() = runBlocking {
        val keoId = host.createKeo()
        b.members.join(keoId, b.uid, b.displayName)

        val members = withTimeout(TIMEOUT_MS) { host.members.observeMembers(keoId).first { it.size == 2 } }

        assertEquals(setOf(host.uid, b.uid), members.map { it.uid }.toSet())
        assertTrue(members.all { it.state == MemberState.JOINED })
    }

    /** S17 Kèo của tôi: truy vấn collectionGroup("members") theo uid. */
    @Test
    fun keoCuaToi_lietKeMoiKeoMinhDangO() = runBlocking {
        val keo1 = host.createKeo(title = "Kèo 1")
        val keo2 = host.createKeo(title = "Kèo 2")
        host.createKeo(title = "Kèo b không vào")
        b.members.join(keo1, b.uid, b.displayName)
        b.members.join(keo2, b.uid, b.displayName)

        val mine = withTimeout(TIMEOUT_MS) { b.members.observeMyMemberships(b.uid).first { it.size == 2 } }

        assertEquals(setOf(keo1, keo2), mine.map { it.keoId }.toSet())
        val docs = b.keos.getKeos(mine.map { it.keoId })
        assertEquals(setOf("Kèo 1", "Kèo 2"), docs.map { it.title }.toSet())
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
