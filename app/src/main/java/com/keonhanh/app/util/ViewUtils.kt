package com.keonhanh.app.util

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.snackbar.Snackbar
import com.keonhanh.app.R
import java.text.NumberFormat
import java.util.Locale

/** 6 màu nền avatar chữ cái đầu (values/colors.xml, kn_avatar_1..6). */
private val avatarColors = intArrayOf(
    R.color.kn_avatar_1,
    R.color.kn_avatar_2,
    R.color.kn_avatar_3,
    R.color.kn_avatar_4,
    R.color.kn_avatar_5,
    R.color.kn_avatar_6,
)

/**
 * Gán avatar chữ cái đầu cho một TextView có style Widget.KeoNhanh.Avatar.
 * Cùng một người luôn ra cùng một màu. Tuần 3 truyền uid của Firebase thay cho tên.
 */
fun TextView.bindAvatar(name: String, key: String = name) {
    setBackgroundResource(R.drawable.bg_avatar)
    text = name.trim().take(1).uppercase()
    setTextColor(ContextCompat.getColor(context, R.color.white))
    val colorRes = avatarColors[(key.hashCode() and Int.MAX_VALUE) % avatarColors.size]
    backgroundTintList = ContextCompat.getColorStateList(context, colorRes)
    contentDescription = name
}

/** Định dạng km kiểu Việt Nam: 0,8 · 1,4 · 2,7 */
fun formatKm(km: Double): String =
    NumberFormat.getNumberInstance(Locale.forLanguageTag("vi")).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }.format(km)

/** Thông báo ngắn cho các nút chưa làm ở tuần 2. */
fun View.showComingSoon() {
    Snackbar.make(this, R.string.coming_soon, Snackbar.LENGTH_SHORT).show()
}

/**
 * Đẩy nội dung lên khi bàn phím mở (app vẽ tràn viền nên adjustResize không tự làm).
 * Gọi trên view gốc của màn có ô nhập ở đáy: S07, S16.
 */
fun View.padForKeyboard() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val navigationBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        view.updatePadding(bottom = (ime - navigationBar).coerceAtLeast(0))
        insets
    }
}
