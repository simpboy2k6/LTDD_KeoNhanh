package com.keonhanh.app.ui.checkin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Đọc mã mời từ chuỗi quét được hoặc gõ tay (S07, liên kết mời). */
class InviteLinkTest {

    @Test
    fun linkDayDu_layDungMa() {
        assertEquals("AB12CD", InviteLink.parse("keonhanh://moi/AB12CD"))
    }

    @Test
    fun maTron_chuThuong_coKhoangTrang_vanNhan() {
        assertEquals("AB12CD", InviteLink.parse("  ab12cd \n"))
    }

    @Test
    fun linkVietHoa_coDauGachCuoi_vanNhan() {
        assertEquals("XYZ789", InviteLink.parse("KEONHANH://MOI/xyz789/"))
    }

    @Test
    fun buildRoiParse_raLaiDungMa() {
        assertEquals("Q1W2E3", InviteLink.parse(InviteLink.build("Q1W2E3")))
    }

    @Test
    fun saiDinhDang_traVeNull() {
        assertNull(InviteLink.parse(null))
        assertNull(InviteLink.parse(""))
        assertNull(InviteLink.parse("ABC12")) // thiếu 1 ký tự
        assertNull(InviteLink.parse("ABC1234")) // thừa 1 ký tự
        assertNull(InviteLink.parse("AB-12C")) // ký tự lạ
        assertNull(InviteLink.parse("https://example.com/moi/AB12CD")) // link của trang khác
    }
}
