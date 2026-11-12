package com.keonhanh.app.data.remote

import com.google.firebase.Timestamp
import com.keonhanh.app.data.model.KeoStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

/**
 * Vòng đời kèo suy ra từ thời gian và số người (slide 10): KeoDoc.effectiveStatus.
 * Chạy: gradlew :app:testDebugUnitTest (không cần máy ảo, không cần Firebase).
 */
class KeoDocStatusTest {

    @Test
    fun `con cho va chua toi han chot thi dang mo`() {
        assertEquals(KeoStatus.OPEN, keo(startInMin = 120, deadlineInMin = 60, memberCount = 1).effectiveStatus(NOW))
    }

    @Test
    fun `du so nguoi truoc han chot thi du nguoi`() {
        assertEquals(KeoStatus.FULL, keo(startInMin = 120, deadlineInMin = 60, memberCount = 4).effectiveStatus(NOW))
    }

    @Test
    fun `qua han chot thi da chot du con cho hay da du`() {
        assertEquals(KeoStatus.LOCKED, keo(startInMin = 60, deadlineInMin = -1, memberCount = 2).effectiveStatus(NOW))
        assertEquals(KeoStatus.LOCKED, keo(startInMin = 60, deadlineInMin = -1, memberCount = 4).effectiveStatus(NOW))
    }

    @Test
    fun `dung han chot la da chot`() {
        assertEquals(KeoStatus.LOCKED, keo(startInMin = 60, deadlineInMin = 0).effectiveStatus(NOW))
    }

    @Test
    fun `check-in mo tu 15 phut truoc toi 15 phut sau gio hen`() {
        assertEquals(KeoStatus.LOCKED, keo(startInMin = 16, deadlineInMin = -30).effectiveStatus(NOW))
        assertEquals(KeoStatus.CHECK_IN, keo(startInMin = 15, deadlineInMin = -30).effectiveStatus(NOW))
        assertEquals(KeoStatus.CHECK_IN, keo(startInMin = 0, deadlineInMin = -30).effectiveStatus(NOW))
        assertEquals(KeoStatus.CHECK_IN, keo(startInMin = -14, deadlineInMin = -60).effectiveStatus(NOW))
    }

    @Test
    fun `qua gio hen 15 phut thi ket thuc`() {
        assertEquals(KeoStatus.ENDED, keo(startInMin = -15, deadlineInMin = -60).effectiveStatus(NOW))
        assertEquals(KeoStatus.ENDED, keo(startInMin = -600, deadlineInMin = -660).effectiveStatus(NOW))
    }

    @Test
    fun `truong status ENDED tren Firestore thang moi suy luan theo gio`() {
        assertEquals(KeoStatus.ENDED, keo(startInMin = 120, deadlineInMin = 60, status = "ENDED").effectiveStatus(NOW))
    }

    @Test
    fun `mon khong nhan ra thi mac dinh bong da de app khong crash`() {
        assertEquals("SOCCER", keo(120, 60).copy(sport = "CHESS").sportEnum.name)
        assertEquals("BOARD_GAME", keo(120, 60).copy(sport = "BOARD_GAME").sportEnum.name)
    }

    companion object {
        const val NOW = 1_800_000_000_000L
        private const val MINUTE = 60_000L

        /** Kèo 4 chỗ; giờ hẹn và hạn chốt tính bằng phút so với NOW (số âm = đã qua). */
        fun keo(
            startInMin: Long,
            deadlineInMin: Long,
            memberCount: Int = 1,
            status: String = "OPEN",
            minTrust: Int? = null,
        ) = KeoDoc(
            id = "keo1",
            hostId = "host",
            sport = "SOCCER",
            title = "Đá bóng 7 người",
            placeName = "Sân bóng gần cổng trường",
            lat = 16.0736,
            lng = 108.15,
            startAt = Timestamp(Date(NOW + startInMin * MINUTE)),
            deadlineAt = Timestamp(Date(NOW + deadlineInMin * MINUTE)),
            capacity = 4,
            memberCount = memberCount,
            minTrust = minTrust,
            status = status,
            inviteCode = "BD7K2Q",
            createdAt = Timestamp(Date(NOW - 60 * MINUTE)),
            updatedAt = null,
        )
    }
}
