package com.keonhanh.app.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.keonhanh.app.R
import com.keonhanh.app.data.remote.FirestorePaths
import com.keonhanh.app.data.remote.MessageDoc
import com.keonhanh.app.data.remote.MessageFields
import com.keonhanh.app.data.remote.snapshotFlow
import com.keonhanh.app.data.remote.toMessageDoc
import com.keonhanh.app.ui.common.AppException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Chat phòng chờ: keos/{keoId}/messages/{msgId}. File này của Phong Hào.
 * Chỉ thành viên đã vào kèo mới đọc và gửi được (luật truy cập trong firestore.rules).
 * Tin nhắn chỉ có chữ, không sửa; người gửi (hoặc chủ kèo) được xóa.
 */
interface ChatRepository {

    /** S16: các tin mới nhất (cũ trước, mới sau), cập nhật realtime. */
    fun observeMessages(keoId: String, limit: Long = MAX_MESSAGES): Flow<List<MessageDoc>>

    /** S15 thẻ "Chat phòng chờ": chỉ tin mới nhất; null nếu phòng chưa có tin nào. */
    fun observeLatestMessage(keoId: String): Flow<MessageDoc?>

    suspend fun send(keoId: String, senderId: String, senderName: String, text: String)

    suspend fun delete(keoId: String, messageId: String)

    companion object {
        /** Gói Spark giới hạn lượt đọc mỗi ngày: mỗi lần mở chat chỉ tải 100 tin gần nhất. */
        const val MAX_MESSAGES = 100L

        /** Trùng với giới hạn trong firestore.rules. */
        const val MAX_LENGTH = 1000
    }
}

class FirestoreChatRepository(
    private val firestore: FirebaseFirestore,
) : ChatRepository {

    private fun messages(keoId: String) =
        firestore.collection(FirestorePaths.KEOS).document(keoId).collection(FirestorePaths.MESSAGES)

    override fun observeMessages(keoId: String, limit: Long): Flow<List<MessageDoc>> =
        // Lấy N tin MỚI nhất (sắp giảm dần rồi limit), sau đó đảo lại để hiện cũ trên, mới dưới
        messages(keoId)
            .orderBy(MessageFields.SENT_AT, Query.Direction.DESCENDING)
            .limit(limit)
            .snapshotFlow()
            .map { snapshot -> snapshot.documents.map { it.toMessageDoc() }.reversed() }

    override fun observeLatestMessage(keoId: String): Flow<MessageDoc?> =
        observeMessages(keoId, limit = 1).map { it.lastOrNull() }

    override suspend fun send(keoId: String, senderId: String, senderName: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (clean.length > ChatRepository.MAX_LENGTH) throw AppException(R.string.error_message_too_long)

        messages(keoId).add(
            mapOf(
                MessageFields.TEXT to clean,
                MessageFields.SENDER_ID to senderId,
                MessageFields.SENDER_NAME to senderName,
                // Giờ của server, không lấy giờ máy: máy mỗi người có thể lệch giờ nên thứ tự tin sẽ sai
                MessageFields.SENT_AT to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun delete(keoId: String, messageId: String) {
        messages(keoId).document(messageId).delete().await()
    }
}
