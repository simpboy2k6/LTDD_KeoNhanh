package com.keonhanh.app.emulator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.keonhanh.app.data.model.Sport
import com.keonhanh.app.data.remote.FirestorePaths
import com.keonhanh.app.data.remote.KeoDraft
import com.keonhanh.app.data.remote.KeoFields
import com.keonhanh.app.data.remote.MemberRole
import com.keonhanh.app.data.remote.MemberState
import com.keonhanh.app.data.remote.newMemberMap
import com.keonhanh.app.data.remote.toNewKeoMap
import com.keonhanh.app.data.repository.AppException
import com.keonhanh.app.data.repository.FirestoreChatRepository
import com.keonhanh.app.data.repository.FirestoreKeoRepository
import com.keonhanh.app.data.repository.FirestoreMemberRepository
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import java.util.Date
import java.util.concurrent.atomic.AtomicLong

/*
 * Hạ tầng cho các test chạy trên máy ảo Android, nối vào Firebase Emulator (Auth + Firestore) trên máy tính.
 * Không đụng tới project Firebase thật. Trước khi chạy:
 *
 *   firebase emulators:start --only auth,firestore --project demo-keonhanh     (ở thư mục gốc của repo)
 *   gradlew :app:connectedDebugAndroidTest
 *
 * Emulator nạp luật từ firestore.rules, nên các test này kiểm tra cả code Repository lẫn luật truy cập.
 */

/** Một "người dùng" độc lập: FirebaseApp riêng nên nhiều người có thể đăng nhập và thao tác cùng lúc. */
class TestUser(val displayName: String, val auth: FirebaseAuth, val firestore: FirebaseFirestore) {
    val uid: String get() = checkNotNull(auth.currentUser) { "$displayName chưa đăng nhập" }.uid

    val keos get() = FirestoreKeoRepository(firestore)
    val members get() = FirestoreMemberRepository(firestore)
    val chat get() = FirestoreChatRepository(firestore)

    fun keoRef(keoId: String): DocumentReference = firestore.collection(FirestorePaths.KEOS).document(keoId)
    fun memberRef(keoId: String, uid: String): DocumentReference = keoRef(keoId).collection(FirestorePaths.MEMBERS).document(uid)
    fun messages(keoId: String) = keoRef(keoId).collection(FirestorePaths.MESSAGES)

    /** Đọc thẳng từ server (không qua cache) để kiểm tra dữ liệu thật sau khi ghi. */
    suspend fun serverGet(ref: DocumentReference): DocumentSnapshot = ref.get(Source.SERVER).await()

    suspend fun memberCount(keoId: String): Int = serverGet(keoRef(keoId)).getLong(KeoFields.MEMBER_COUNT)!!.toInt()

    /**
     * Tạo kèo đúng theo cách đã hướng dẫn Huy Hoàng trong KeoRepository.createKeo:
     * một batch gồm document kèo (memberCount = 1) và document chủ kèo trong members.
     */
    suspend fun createKeo(
        capacity: Int = 4,
        minTrust: Int? = null,
        deadlineInMin: Long = 60,
        startInMin: Long = 120,
        title: String = "Đá bóng 7 người",
    ): String {
        val keoRef = firestore.collection(FirestorePaths.KEOS).document()
        val draft = KeoDraft(
            sport = Sport.SOCCER,
            title = title,
            placeName = "Sân bóng gần cổng trường",
            lat = 16.0736,
            lng = 108.15,
            startAt = minutesFromNow(startInMin),
            deadlineAt = minutesFromNow(deadlineInMin),
            capacity = capacity,
            minTrust = minTrust,
        )
        firestore.batch()
            .set(keoRef, draft.toNewKeoMap(uid, inviteCode = "T" + counter.incrementAndGet()))
            .set(memberRef(keoRef.id, uid), newMemberMap(uid, displayName, MemberRole.HOST, MemberState.JOINED))
            .commit().await()
        return keoRef.id
    }

    /** Chủ kèo dời hạn chốt về quá khứ để thử trạng thái "Đã chốt" mà không phải chờ. */
    suspend fun moveDeadlineToPast(keoId: String) {
        keoRef(keoId).update(KeoFields.DEADLINE_AT, minutesFromNow(-1)).await()
    }

    /** Chủ kèo dời giờ hẹn về sát hiện tại để thử trạng thái "Check-in" (giờ hẹn ± 15 phút). */
    suspend fun moveToCheckInWindow(keoId: String) {
        keoRef(keoId).update(KeoFields.DEADLINE_AT, minutesFromNow(-30), KeoFields.START_AT, minutesFromNow(5)).await()
    }

    private companion object {
        val counter = AtomicLong(System.currentTimeMillis() % 100_000)
    }
}

object FirebaseEmulator {
    /** Địa chỉ máy tính nhìn từ máy ảo Android. */
    private const val HOST = "10.0.2.2"
    private const val AUTH_PORT = 9099
    private const val FIRESTORE_PORT = 8080
    private const val PASSWORD = "matkhau123"

    /** Người dùng của chính app (FirebaseApp mặc định): màn hình thật trong test giao diện dùng người này. */
    const val APP_USER = "[DEFAULT]"

    private val users = mutableMapOf<String, TestUser>()
    private val counter = AtomicLong(System.nanoTime())

    /**
     * Đăng ký một tài khoản mới tinh trên Auth Emulator và đăng nhập luôn.
     * @param key mỗi key là một FirebaseApp riêng ("host", "b", "c"...), dùng [APP_USER] cho app mặc định
     */
    suspend fun signInNew(key: String, displayName: String): TestUser {
        val user = userFor(key, displayName)
        user.auth.signOut()
        user.auth.createUserWithEmailAndPassword("${displayName.lowercase()}.${counter.incrementAndGet()}@sv.test.vn", PASSWORD).await()
        return user
    }

    /** Một người chưa đăng nhập (để thử luật chặn người lạ). */
    fun signedOut(key: String): TestUser = userFor(key, "khach").also { it.auth.signOut() }

    @Synchronized
    private fun userFor(key: String, displayName: String): TestUser {
        val existing = users[key]
        if (existing != null) return TestUser(displayName, existing.auth, existing.firestore).also { users[key] = it }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val default = FirebaseApp.getInstance()
        val app = if (key == APP_USER) default else FirebaseApp.initializeApp(context, default.options, key)
        // useEmulator phải gọi trước mọi thao tác khác trên instance đó
        val auth = FirebaseAuth.getInstance(app).apply { useEmulator(HOST, AUTH_PORT) }
        val firestore = FirebaseFirestore.getInstance(app).apply { useEmulator(HOST, FIRESTORE_PORT) }
        return TestUser(displayName, auth, firestore).also { users[key] = it }
    }
}

fun minutesFromNow(minutes: Long): Timestamp = Timestamp(Date(System.currentTimeMillis() + minutes * 60_000))

/** Khối lệnh phải bị luật truy cập từ chối (PERMISSION_DENIED). */
suspend fun assertDenied(what: String, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: FirebaseFirestoreException) {
        assertEquals("$what: phải bị từ chối quyền", FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        return
    }
    fail("$what: lẽ ra phải bị luật truy cập chặn nhưng lại ghi/đọc được")
}

/** Khối lệnh phải ném AppException với đúng câu thông báo [messageRes]. */
suspend fun assertAppError(messageRes: Int, block: suspend () -> Unit) {
    try {
        block()
    } catch (e: Throwable) {
        val app = generateSequence(e) { it.cause }.filterIsInstance<AppException>().firstOrNull()
        if (app == null) throw AssertionError("Chờ AppException nhưng nhận ${e::class.java.simpleName}: ${e.message}", e)
        assertEquals("Sai câu thông báo lỗi", messageRes, app.messageRes)
        return
    }
    fail("Lẽ ra phải báo lỗi nghiệp vụ nhưng lại chạy xong")
}
