package com.keonhanh.app.ui.common

import android.content.Context
import androidx.annotation.StringRes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.keonhanh.app.R

/**
 * Trạng thái chung của một màn hình có đọc dữ liệu (mọi ViewModel tuần 3 dùng chung).
 * Fragment chỉ cần `when (state)` để hiện vòng tải, nội dung hoặc lỗi:
 *
 *   when (state) {
 *       UiState.Loading -> ...hiện progress
 *       is UiState.Success -> ...vẽ state.data
 *       is UiState.Error -> ...hiện state.message.resolve(requireContext())
 *   }
 *
 * (AuthViewModel của Minh Khoa có AuthUiState riêng vì màn đăng nhập còn trạng thái Idle; không cần đổi.)
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: UiText) : UiState<Nothing>
}

/**
 * Chữ hiện cho người dùng mà ViewModel tạo ra. ViewModel không giữ Context nên không gọi getString được:
 * ViewModel trả về id chuỗi (Res), Fragment mới đổi thành chữ bằng [resolve].
 * Raw chỉ dùng cho thông báo lỗi lấy nguyên văn từ Firebase.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val text: String) : UiText

    fun resolve(context: Context): String = when (this) {
        is Res -> context.getString(id, *args.toTypedArray())
        is Raw -> text
    }
}

/**
 * Lỗi nghiệp vụ có thông báo sẵn trong strings.xml, ví dụ "Kèo đã đủ người".
 * Repository ném lỗi này khi luật của app không cho làm (khác với lỗi mạng hay lỗi Firebase).
 */
class AppException(@StringRes val messageRes: Int) : Exception()

/** Đổi một lỗi bất kỳ thành chữ cho người dùng. Lỗi trong transaction thường bị bọc nhiều lớp nên phải tìm theo chuỗi cause. */
fun Throwable.toUiText(): UiText {
    generateSequence(this) { it.cause }.forEach { error ->
        when {
            error is AppException -> return UiText.Res(error.messageRes)
            // Hàm còn TODO(...) của bạn khác trong nhóm: báo nhẹ nhàng thay vì in nguyên lời nhắn TODO
            error is NotImplementedError -> return UiText.Res(R.string.error_not_ready)
            error is FirebaseNetworkException -> return UiText.Res(R.string.error_network)
            error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                return UiText.Res(R.string.error_permission)
            error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
                return UiText.Res(R.string.error_network)
        }
    }
    return message?.let { UiText.Raw(it) } ?: UiText.Res(R.string.error_generic)
}
