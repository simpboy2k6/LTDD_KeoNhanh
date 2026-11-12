package com.keonhanh.app.ui.lobby

import com.keonhanh.app.data.model.Keo
import com.keonhanh.app.data.model.KeoStatus
import com.keonhanh.app.data.model.MyRole
import com.keonhanh.app.data.model.Sport
import com.keonhanh.app.data.remote.MemberState
import org.junit.Assert.assertEquals
import org.junit.Test

/** Bảng nút ở S15 (mục 3 tài liệu tuần 2): trạng thái kèo × vai trò của mình. */
class PrimaryActionTest {

    @Test
    fun `dang mo - nguoi ngoai tham gia hoac xin vao`() {
        assertEquals(PrimaryAction.JOIN, action(KeoStatus.OPEN, MyRole.NONE))
        assertEquals(PrimaryAction.REQUEST_JOIN, action(KeoStatus.OPEN, MyRole.NONE, minTrust = 70))
    }

    @Test
    fun `dang mo - da xin vao thi cho huy yeu cau`() {
        assertEquals(PrimaryAction.CANCEL_REQUEST, action(KeoStatus.OPEN, MyRole.NONE, minTrust = 70, myState = MemberState.REQUESTED))
        // Kèo đủ người trong lúc chờ duyệt: vẫn hủy yêu cầu được
        assertEquals(PrimaryAction.CANCEL_REQUEST, action(KeoStatus.FULL, MyRole.NONE, minTrust = 70, myState = MemberState.REQUESTED))
    }

    @Test
    fun `dang mo va du nguoi - thanh vien roi keo, chu keo huy keo`() {
        for (status in listOf(KeoStatus.OPEN, KeoStatus.FULL)) {
            assertEquals(PrimaryAction.LEAVE, action(status, MyRole.MEMBER, myState = MemberState.JOINED))
            assertEquals(PrimaryAction.CANCEL_KEO, action(status, MyRole.HOST, myState = MemberState.JOINED))
        }
    }

    @Test
    fun `du nguoi - nguoi ngoai thay nut mo`() {
        assertEquals(PrimaryAction.FULL, action(KeoStatus.FULL, MyRole.NONE))
        assertEquals(PrimaryAction.FULL, action(KeoStatus.FULL, MyRole.NONE, minTrust = 70))
        assertEquals(false, PrimaryAction.FULL.enabled)
    }

    @Test
    fun `da chot - thanh vien khong roi duoc, chu keo van huy duoc`() {
        assertEquals(PrimaryAction.LOCKED, action(KeoStatus.LOCKED, MyRole.NONE))
        assertEquals(PrimaryAction.LOCKED_MEMBER, action(KeoStatus.LOCKED, MyRole.MEMBER, myState = MemberState.JOINED))
        assertEquals(PrimaryAction.CANCEL_KEO, action(KeoStatus.LOCKED, MyRole.HOST, myState = MemberState.JOINED))
        assertEquals(false, PrimaryAction.LOCKED_MEMBER.enabled)
    }

    @Test
    fun `check-in va ket thuc - khong con thao tac vao ra`() {
        for (role in MyRole.entries) {
            assertEquals(PrimaryAction.CHECKING_IN, action(KeoStatus.CHECK_IN, role))
            assertEquals(PrimaryAction.ENDED, action(KeoStatus.ENDED, role))
        }
    }

    private fun action(status: KeoStatus, role: MyRole, minTrust: Int? = null, myState: MemberState? = null) =
        primaryActionFor(
            Keo(
                id = "keo1", title = "Đá bóng", sport = Sport.SOCCER, place = "Sân", distanceKm = 0.0, time = "",
                deadlineCountdown = "", hostName = "Minh", hostTrust = 0, members = emptyList(), maxSlots = 4,
                minTrust = minTrust, inviteCode = "BD7K2Q", status = status, myRole = role,
            ),
            myState,
        )
}
