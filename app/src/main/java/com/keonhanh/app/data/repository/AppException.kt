package com.keonhanh.app.data.repository

import androidx.annotation.StringRes

/**
 * Lỗi nghiệp vụ có thông báo sẵn trong strings.xml, ví dụ "Kèo đã đủ người".
 * Repository ném lỗi này khi luật của app không cho làm (khác với lỗi mạng hay lỗi Firebase);
 * ViewModel đổi sang chữ cho người dùng bằng toUiText() trong ui/common/UiState.kt.
 */
class AppException(@StringRes val messageRes: Int) : Exception()
