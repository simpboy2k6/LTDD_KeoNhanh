package com.keonhanh.app.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.keonhanh.app.data.repository.AuthRepository
import com.keonhanh.app.data.repository.ChatRepository
import com.keonhanh.app.data.repository.FirebaseAuthRepository
import com.keonhanh.app.data.repository.FirestoreChatRepository
import com.keonhanh.app.data.repository.FirestoreKeoRepository
import com.keonhanh.app.data.repository.FirestoreMemberRepository
import com.keonhanh.app.data.repository.FirestoreUserRepository
import com.keonhanh.app.data.repository.KeoRepository
import com.keonhanh.app.data.repository.MemberRepository
import com.keonhanh.app.data.repository.UserRepository

/**
 * Nơi duy nhất tạo Firebase và các Repository (Phong Hào giữ file này).
 * KeoNhanhApp tạo một AppContainer khi mở app; Fragment lấy ra qua `appViewModels { container -> ... }`.
 *
 * Mỗi Repository khai báo kiểu là interface (KeoRepository...), tạo bằng lớp Firestore...:
 * ViewModel chỉ biết interface, muốn demo không mạng thì đổi sang bản Fake ở đúng chỗ này.
 * `by lazy`: chỉ tạo khi có màn hình dùng tới lần đầu.
 *
 * Cần thêm Repository mới thì nhắn Phong Hào thêm vào đây, đừng tự sửa để tránh conflict khi merge.
 */
class AppContainer {
    val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    /** Minh Khoa: đăng ký, đăng nhập, đăng xuất. */
    val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(firebaseAuth)
    }

    /** Minh Khoa: hồ sơ users/{uid}. */
    val userRepository: UserRepository by lazy {
        FirestoreUserRepository(firestore)
    }

    /** Huy Hoàng: tạo, đọc, sửa, xóa kèo. */
    val keoRepository: KeoRepository by lazy {
        FirestoreKeoRepository(firestore)
    }

    /** Phong Hào: tham gia, xin vào, rời kèo. */
    val memberRepository: MemberRepository by lazy {
        FirestoreMemberRepository(firestore)
    }

    /** Phong Hào: chat phòng chờ. */
    val chatRepository: ChatRepository by lazy {
        FirestoreChatRepository(firestore)
    }
}
