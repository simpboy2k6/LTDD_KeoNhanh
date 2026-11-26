package com.keonhanh.app.emulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.keonhanh.app.data.model.Sport
import com.keonhanh.app.data.remote.KeoDraft
import com.keonhanh.app.data.remote.MemberRole
import com.keonhanh.app.data.remote.MemberState
import com.keonhanh.app.data.remote.toKeo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** KeoRepository.createKeo (Huy Hoàng) chạy thật trên Firestore Emulator. */
@RunWith(AndroidJUnit4::class)
class KeoRepositoryEmulatorTest {

    /** BUG-05: tạo kèo xong, màn Chi tiết kèo phải hiện đúng tên chủ kèo (trước đây trống). */
    @Test
    fun taoKeo_tenChuKeoHienTrenChiTietKeo() = runBlocking {
        val host = FirebaseEmulator.signInNew("bug05", "Hao")
        val draft = KeoDraft(
            sport = Sport.BADMINTON,
            title = "Cầu lông tối thứ 6",
            placeName = "Nhà thi đấu Bách khoa",
            lat = 16.0748,
            lng = 108.1512,
            startAt = minutesFromNow(120),
            deadlineAt = minutesFromNow(60),
            capacity = 4,
            minTrust = null,
        )

        val keoId = host.keos.createKeo(draft, host.uid, "Phong Hào")

        val members = host.members.getMembers(keoId)
        val hostRow = members.single()
        assertEquals(host.uid, hostRow.uid)
        assertEquals(MemberRole.HOST, hostRow.role)
        assertEquals(MemberState.JOINED, hostRow.state)
        assertEquals("Phong Hào", hostRow.displayName)

        // Đúng đường dữ liệu của S15: KeoDoc + thành viên -> Keo.hostName
        val keo = host.keos.getKeos(listOf(keoId)).single().toKeo(members, host.uid)
        assertEquals("Phong Hào", keo.hostName)
    }
}
