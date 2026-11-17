package com.keonhanh.app.util

import java.text.Normalizer

// Khai báo là 'object' để có thể gọi trực tiếp StringUtil.removeAccent(...)
object StringUtil {
    fun removeAccent(str: String): String {
        val temp = Normalizer.normalize(str, Normalizer.Form.NFD)
        val regex = "\\p{InCombiningDiacriticalMarks}+".toRegex()
        return regex.replace(temp, "").replace("đ", "d").replace("Đ", "D")
    }
}