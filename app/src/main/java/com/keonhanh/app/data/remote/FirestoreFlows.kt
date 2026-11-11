package com.keonhanh.app.data.remote

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/*
 * Đổi listener realtime của Firestore thành Flow của Kotlin.
 * - Mỗi lần dữ liệu trên server đổi, Flow phát ra snapshot mới.
 * - Khi màn hình không còn collect (ViewModel bị hủy), awaitClose gỡ listener: không rò rỉ, không tốn lượt đọc.
 * - Lỗi (mất quyền, sai luật truy cập...) đi theo Flow; ViewModel bắt bằng .catch { }.
 */

/** Nghe một document. */
fun DocumentReference.snapshotFlow(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
        } else if (snapshot != null) {
            trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}

/** Nghe kết quả một truy vấn (danh sách document). */
fun Query.snapshotFlow(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
        } else if (snapshot != null) {
            trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}
