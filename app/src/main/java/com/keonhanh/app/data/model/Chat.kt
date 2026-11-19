package com.keonhanh.app.data.model

/** Một tin nhắn trong phòng chờ. Chỉ có chữ (gói Spark không có Cloud Storage để gửi ảnh). */
data class Message(
    val id: String,
    val sender: Member,
    val text: String,
    val time: String,
    val isMine: Boolean,
)

/**
 * Một dòng ở S18 Danh sách chat.
 * Tuần 4: lastMessage rỗng nghĩa là phòng chưa có tin; senderName và isMine để hiện "Bạn: ..." hoặc "Khoa: ...".
 */
data class ChatRoom(
    val keo: Keo,
    val lastMessage: String,
    val time: String,
    val senderName: String = "",
    val isMine: Boolean = false,
)
