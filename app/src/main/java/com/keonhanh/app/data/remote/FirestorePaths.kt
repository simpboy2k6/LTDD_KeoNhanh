package com.keonhanh.app.data.remote

/**
 * Tên collection và tên trường trên Firestore, khớp bảng dữ liệu ở mục 4 kế hoạch tuần 3.
 * Mọi Repository lấy tên ở đây, không gõ chuỗi "keos", "members"... rải rác trong code:
 * gõ sai một chữ là Firestore lặng lẽ đọc về rỗng, rất khó tìm lỗi.
 *
 * Sau mốc 2 nhóm đã chốt tên trường. Muốn thêm trường thì thêm ở đây và báo cả nhóm.
 */
object FirestorePaths {
    const val USERS = "users"

    /** keos/{keoId} */
    const val KEOS = "keos"

    /** keos/{keoId}/members/{uid}: id của document chính là uid nên một người chỉ có 1 dòng trong 1 kèo. */
    const val MEMBERS = "members"

    /** keos/{keoId}/messages/{msgId} */
    const val MESSAGES = "messages"

    /** users/{uid}/places/{placeId} (S14, P1) */
    const val PLACES = "places"
}

/** Trường của document keos/{keoId} (Huy Hoàng giữ). */
object KeoFields {
    const val HOST_ID = "hostId"
    const val SPORT = "sport"
    const val TITLE = "title"
    const val PLACE_NAME = "placeName"
    const val LAT = "lat"
    const val LNG = "lng"
    const val START_AT = "startAt"
    const val DEADLINE_AT = "deadlineAt"
    const val CAPACITY = "capacity"

    /** Lưu sẵn số thành viên JOINED; chỉ đổi trong transaction cùng lúc thêm/xóa MEMBER. */
    const val MEMBER_COUNT = "memberCount"
    const val MIN_TRUST = "minTrust"
    const val STATUS = "status"
    const val INVITE_CODE = "inviteCode"
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
}

/** Trường của document keos/{keoId}/members/{uid} (Phong Hào giữ). */
object MemberFields {
    /** Lặp lại uid trong document để truy vấn collectionGroup("members") theo người dùng (S17). */
    const val UID = "uid"

    /** Lưu sẵn tên lúc vào kèo để S15 không phải đọc users/{uid} cho từng người. */
    const val DISPLAY_NAME = "displayName"
    const val ROLE = "role"
    const val STATE = "state"
    const val JOINED_AT = "joinedAt"
    const val CHECK_IN_AT = "checkInAt"
    const val CHECK_IN_DISTANCE_M = "checkInDistanceM"
    const val ATTENDANCE = "attendance"
}

/** Trường của document keos/{keoId}/messages/{msgId} (Phong Hào giữ). */
object MessageFields {
    const val TEXT = "text"
    const val SENDER_ID = "senderId"

    /** Lưu sẵn tên người gửi để mỗi tin không phải đọc thêm users/{uid}. */
    const val SENDER_NAME = "senderName"
    const val SENT_AT = "sentAt"
}
