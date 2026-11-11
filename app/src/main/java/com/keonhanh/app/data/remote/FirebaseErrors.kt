package com.keonhanh.app.data.remote

import androidx.annotation.StringRes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.keonhanh.app.R

/**
 * Câu thông báo cho các lỗi Firebase hay gặp; null nếu không phải lỗi Firebase quen thuộc.
 * Đặt ở data/ để tầng ui/ không phải import Firebase (checklist mục 10 kế hoạch tuần 3).
 */
@StringRes
fun Throwable.firebaseMessageRes(): Int? = when {
    this is FirebaseNetworkException -> R.string.error_network
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> R.string.error_permission
    this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.UNAVAILABLE -> R.string.error_network
    else -> null
}
