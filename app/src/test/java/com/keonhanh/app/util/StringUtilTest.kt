package com.keonhanh.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StringUtilTest {

    @Test
    fun `bo dau tieng Viet chuan xac`() {
        assertEquals("Cau long", StringUtil.removeAccent("Cầu lông"))
        assertEquals("Da bong san 7 nguoi", StringUtil.removeAccent("Đá bóng sân 7 người"))
        assertEquals("Keo Nhanh Da Nang", StringUtil.removeAccent("Kèo Nhanh Đà Nẵng"))
    }

    @Test
    fun `tim kiem khong dau khop voi chuoi co dau`() {
        val title = StringUtil.removeAccent("Giao lưu Cầu Lông tối thứ 5")
        val query = StringUtil.removeAccent("cau long")
        assertTrue(title.contains(query, ignoreCase = true))
    }
}