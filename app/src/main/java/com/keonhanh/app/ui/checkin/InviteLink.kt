package com.keonhanh.app.ui.checkin

import android.graphics.Bitmap
import androidx.annotation.ColorInt
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Liên kết mời vào kèo (Phong Hào, tuần 4): keonhanh://moi/{code}, code là mã mời 6 ký tự của kèo.
 * Cùng một chuỗi được dùng ở 3 chỗ: nội dung mã QR (S06), nút Chia sẻ (S06) và intent-filter của MainActivity.
 */
object InviteLink {

    const val PREFIX = "keonhanh://moi/"

    /** Mã mời do KeoRepository sinh: 6 ký tự A–Z, 0–9. */
    private val CODE = Regex("^[A-Z0-9]{6}$")

    fun build(code: String): String = PREFIX + code

    /**
     * Lấy mã mời từ chuỗi quét được hoặc người dùng gõ. Nhận cả link đầy đủ lẫn mã trơn,
     * không phân biệt hoa thường, bỏ khoảng trắng và dấu "/" cuối. Trả về null nếu không phải mã mời.
     */
    fun parse(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        val code = if (text.startsWith(PREFIX, ignoreCase = true)) text.substring(PREFIX.length) else text
        return code.trim().trimEnd('/').uppercase().takeIf { CODE.matches(it) }
    }
}

/**
 * Vẽ mã QR bằng ZXing (chỉ phần tạo mã, không cần camera). Chạy ở luồng nền vì phải tô từng điểm ảnh.
 * margin = 1: viền trắng mỏng, ảnh QR to hơn trong cùng một khung.
 */
fun qrBitmap(content: String, sizePx: Int, @ColorInt dark: Int, @ColorInt light: Int): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height) { i -> if (matrix[i % width, i / width]) dark else light }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}
