package com.keonhanh.app.data.remote

import com.google.firebase.Timestamp
import com.keonhanh.app.data.model.KeoStatus
import com.keonhanh.app.data.model.MyRole
import com.keonhanh.app.data.remote.KeoDocStatusTest.Companion.NOW
import com.keonhanh.app.data.remote.KeoDocStatusTest.Companion.keo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

/** Chuyển đổi Doc -> model hiển thị và định dạng thời gian (data/remote/Mappers.kt). */
class MappersTest {

    @Test
    fun `dem nguoc duoi 1 gio hien phut giay, tren 1 gio hien ca gio`() {
        assertEquals("24:10", formatCountdown((24 * 60 + 10) * 1000L))
        assertEquals("45:12", formatCountdown((45 * 60 + 12) * 1000L))
        assertEquals("1:58:00", formatCountdown((60 + 58) * 60 * 1000L))
        assertEquals("00:59", formatCountdown(59_999L))
    }

    @Test
    fun `dem nguoc het gio thi dung o 00 00 khong am`() {
        assertEquals("00:00", formatCountdown(0))
        assertEquals("00:00", formatCountdown(-5_000))
    }

    @Test
    fun `khung check-in la gio hen cong tru 15 phut`() {
        val start = LocalDateTime.of(2026, 11, 13, 17, 0).atZone(ZoneId.systemDefault()).toInstant()
        assertEquals("16:45 – 17:15", formatCheckInWindow(Timestamp(Date.from(start))))
    }

    @Test
    fun `luoi thanh vien chi co nguoi JOINED, chu keo dung dau roi theo thu tu vao`() {
        val ui = keo(startInMin = 120, deadlineInMin = 60, memberCount = 3).toKeo(MEMBERS, myUid = "b", nowMillis = NOW)

        assertEquals(listOf("host", "c", "b"), ui.members.map { it.id })
        assertEquals("Minh", ui.hostName)
        assertEquals(3, ui.memberCount)
        assertEquals(4, ui.maxSlots)
        assertEquals(KeoStatus.OPEN, ui.status)
    }

    @Test
    fun `vai tro cua minh theo dong members`() {
        val doc = keo(startInMin = 120, deadlineInMin = 60, memberCount = 3)

        assertEquals(MyRole.HOST, doc.toKeo(MEMBERS, myUid = "host", nowMillis = NOW).myRole)
        assertEquals(MyRole.MEMBER, doc.toKeo(MEMBERS, myUid = "b", nowMillis = NOW).myRole)
        // Đang xin vào (REQUESTED) thì chưa phải thành viên
        assertEquals(MyRole.NONE, doc.toKeo(MEMBERS, myUid = "d", nowMillis = NOW).myRole)
        assertEquals(MyRole.NONE, doc.toKeo(MEMBERS, myUid = "nguoi-la", nowMillis = NOW).myRole)
        assertEquals(MyRole.NONE, doc.toKeo(MEMBERS, myUid = null, nowMillis = NOW).myRole)
    }

    @Test
    fun `danh sach khong tai thanh vien van hien dung so nguoi tu memberCount`() {
        val ui = keo(startInMin = 120, deadlineInMin = 60, memberCount = 3).toKeo(emptyList(), myUid = "b", nowMillis = NOW)

        assertTrue(ui.members.isEmpty())
        assertEquals(3, ui.memberCount)
    }

    @Test
    fun `da check-in khi co checkInAt`() {
        val checkedIn = MEMBERS.map { if (it.uid == "c") it.copy(checkInAt = at(500)) else it }
        val ui = keo(startInMin = 0, deadlineInMin = -60, memberCount = 3).toKeo(checkedIn, myUid = "b", nowMillis = NOW)

        assertEquals(KeoStatus.CHECK_IN, ui.status)
        assertEquals(listOf("c"), ui.members.filter { it.checkedIn }.map { it.id })
    }

    @Test
    fun `dem nguoc tren the keo tinh tu han chot`() {
        val ui = keo(startInMin = 120, deadlineInMin = 45, memberCount = 1).toKeo(emptyList(), myUid = null, nowMillis = NOW)
        assertEquals("45:00", ui.deadlineCountdown)
    }

    @Test
    fun `tin nhan cua minh nam ben phai`() {
        val doc = MessageDoc(id = "m1", senderId = "b", senderName = "Bình", text = "Mình tới trễ 5 phút", sentAt = at(100))

        assertTrue(doc.toMessage(myUid = "b").isMine)
        assertFalse(doc.toMessage(myUid = "c").isMine)
        assertFalse(doc.toMessage(myUid = null).isMine)
        assertEquals("Bình", doc.toMessage("c").sender.name)
    }

    @Test
    fun `tin dang cho gio server van hien duoc`() {
        val pending = MessageDoc(id = "m2", senderId = "b", senderName = "Bình", text = "ok", sentAt = null)
        assertEquals("", pending.toMessage("b").time)
    }

    private companion object {
        fun at(seconds: Long) = Timestamp(seconds, 0)

        val MEMBERS = listOf(
            MemberDoc("b", "Bình", MemberRole.MEMBER, MemberState.JOINED, joinedAt = at(300)),
            MemberDoc("d", "Dung", MemberRole.MEMBER, MemberState.REQUESTED, joinedAt = at(50)),
            MemberDoc("host", "Minh", MemberRole.HOST, MemberState.JOINED, joinedAt = at(100)),
            MemberDoc("c", "Chi", MemberRole.MEMBER, MemberState.JOINED, joinedAt = at(200)),
        )
    }
}
