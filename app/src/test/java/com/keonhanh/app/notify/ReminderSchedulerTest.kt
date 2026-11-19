package com.keonhanh.app.notify

import com.keonhanh.app.ui.chat.formatChatTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Tính giờ nhắc kèo (ReminderScheduler) và giờ hiện ở danh sách chat. */
class ReminderSchedulerTest {

    private val minute = 60_000L
    private val now = 1_800_000_000_000L

    @Test
    fun keoConXa_nhacTruoc30Phut() {
        assertEquals(90 * minute, ReminderScheduler.delayMillis(startAtMillis = now + 120 * minute, nowMillis = now))
    }

    @Test
    fun vaoKeoKhiConDuoi30Phut_nhacNgay() {
        assertEquals(0L, ReminderScheduler.delayMillis(startAtMillis = now + 10 * minute, nowMillis = now))
    }

    @Test
    fun keoDaBatDau_khongNhac() {
        assertNull(ReminderScheduler.delayMillis(startAtMillis = now, nowMillis = now))
        assertNull(ReminderScheduler.delayMillis(startAtMillis = now - minute, nowMillis = now))
    }

    @Test
    fun gioChat_homNayHienGio_ngayKhacHienNgay() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val nowAt = ZonedDateTime.of(2026, 11, 18, 21, 0, 0, 0, zone)
        val today = ZonedDateTime.of(2026, 11, 18, 19, 30, 0, 0, zone)
        val yesterday = ZonedDateTime.of(2026, 11, 17, 23, 59, 0, 0, zone)
        val nowMillis = nowAt.toInstant().toEpochMilli()
        assertEquals("19:30", formatChatTime(today.toInstant().toEpochMilli(), nowMillis, zone))
        assertEquals("17/11", formatChatTime(yesterday.toInstant().toEpochMilli(), nowMillis, zone))
    }
}
