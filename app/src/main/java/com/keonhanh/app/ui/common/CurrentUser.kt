package com.keonhanh.app.ui.common

import com.keonhanh.app.data.repository.AuthRepository
import com.keonhanh.app.data.repository.UserRepository

/**
 * Tên hiển thị của người đang đăng nhập, dùng khi vào kèo (members.displayName) và khi gửi tin (messages.senderName).
 * Lấy từ hồ sơ users/{uid}; chưa đọc được hồ sơ thì dùng phần trước dấu @ của email.
 */
suspend fun resolveMyName(authRepository: AuthRepository, userRepository: UserRepository): String {
    val user = authRepository.currentUser ?: return ""
    val profileName = userRepository.getUserProfile(user.uid).getOrNull()?.name
    return profileName?.takeIf { it.isNotBlank() }
        ?: user.displayName?.takeIf { it.isNotBlank() }
        ?: user.email?.substringBefore('@').orEmpty()
}
