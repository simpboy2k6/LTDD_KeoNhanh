package com.keonhanh.app.util

import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.keonhanh.app.R


object AuthFireBaseUtils {

    // Hàm này nhận vào Exception trả về từ Firebase và xuất ra ID của string tiếng Việt
    fun translateAuthError(e: Exception): Int {
        return when (e) {
            is FirebaseAuthInvalidUserException -> {
                // Lỗi không tìm thấy user
                R.string.err_user_not_found
            }
            is FirebaseAuthInvalidCredentialsException -> {
                // Sai mật khẩu hoặc email không hợp lệ
                if (e.errorCode == "ERROR_INVALID_EMAIL") R.string.err_invalid_email else R.string.err_wrong_password
            }
            is FirebaseAuthUserCollisionException -> {
                // Email đã có người đăng ký
                R.string.err_email_in_use
            }
            is FirebaseAuthException -> {
                // Xử lý các mã lỗi chung khác dựa vào errorCode
                when (e.errorCode) {
                    "ERROR_WEAK_PASSWORD" -> R.string.err_weak_password
                    "ERROR_NETWORK_REQUEST_FAILED" -> R.string.err_network_request_failed
                    else -> R.string.err_unknown
                }
            }
            else -> R.string.err_unknown
        }
    }
}