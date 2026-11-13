package com.keonhanh.app.emulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.Source
import com.keonhanh.app.R
import com.keonhanh.app.data.repository.ChatRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** ChatRepository và phần đọc của KeoRepository chạy thật trên Firestore Emulator. */
@RunWith(AndroidJUnit4::class)
class ChatRepositoryEmulatorTest {

    private lateinit var host: TestUser
    private lateinit var b: TestUser

    @Before
    fun signIn() = runBlocking {
        host = FirebaseEmulator.signInNew("host", "Minh")
        b = FirebaseEmulator.signInNew("b", "Binh")
    }

    @Test
    fun guiTin_nguoiKhacTrongKeoNhanDuoc_theoDungThuTu() = runBlocking {
        val keoId = host.createKeo()
        b.members.join(keoId, b.uid, b.displayName)

        host.chat.send(keoId, host.uid, host.displayName, "Mình mang bóng nhé")
        b.chat.send(keoId, b.uid, b.displayName, "  Ok, 18:30 có mặt  ")
        host.chat.send(keoId, host.uid, host.displayName, "Chốt vậy")

        val messages = withTimeout(TIMEOUT_MS) { b.chat.observeMessages(keoId).first { it.size == 3 && it.all { m -> m.sentAt != null } } }

        assertEquals(listOf("Mình mang bóng nhé", "Ok, 18:30 có mặt", "Chốt vậy"), messages.map { it.text })
        assertEquals(listOf("Minh", "Binh", "Minh"), messages.map { it.senderName })
        assertEquals(listOf(host.uid, b.uid, host.uid), messages.map { it.senderId })
    }

    @Test
    fun tinMoiNhat_choTheXemTruocOS15() = runBlocking {
        val keoId = host.createKeo()
        assertNull(withTimeout(TIMEOUT_MS) { host.chat.observeLatestMessage(keoId).first() })

        host.chat.send(keoId, host.uid, host.displayName, "Tin 1")
        host.chat.send(keoId, host.uid, host.displayName, "Tin 2")

        val latest = withTimeout(TIMEOUT_MS) { host.chat.observeLatestMessage(keoId).first { it?.text == "Tin 2" } }
        assertNotNull(latest)
    }

    @Test
    fun xoaTinCuaMinh() = runBlocking {
        val keoId = host.createKeo()
        host.chat.send(keoId, host.uid, host.displayName, "Gửi nhầm")
        val message = withTimeout(TIMEOUT_MS) { host.chat.observeMessages(keoId).first { it.size == 1 } }.single()

        host.chat.delete(keoId, message.id)

        assertEquals(0, host.messages(keoId).get(Source.SERVER).await().size())
    }

    @Test
    fun tinRongBiBoQua_tinQuaDaiBaoLoi() = runBlocking {
        val keoId = host.createKeo()

        host.chat.send(keoId, host.uid, host.displayName, "   ")
        assertEquals(0, host.messages(keoId).get(Source.SERVER).await().size())

        assertAppError(R.string.error_message_too_long) {
            host.chat.send(keoId, host.uid, host.displayName, "a".repeat(ChatRepository.MAX_LENGTH + 1))
        }
        // Đúng 1000 ký tự thì gửi được (khớp giới hạn trong firestore.rules)
        host.chat.send(keoId, host.uid, host.displayName, "a".repeat(ChatRepository.MAX_LENGTH))
        assertEquals(1, host.messages(keoId).get(Source.SERVER).await().size())
    }

    @Test
    fun ngheMotKeo_nhanDuLieu_vaNullKhiKeoBiXoa() = runBlocking {
        val keoId = host.createKeo(capacity = 5, minTrust = 70, title = "Cầu lông tối nay")

        val doc = withTimeout(TIMEOUT_MS) { b.keos.observeKeo(keoId).first { it != null } }!!
        assertEquals("Cầu lông tối nay", doc.title)
        assertEquals(host.uid, doc.hostId)
        assertEquals(5, doc.capacity)
        assertEquals(1, doc.memberCount)
        assertEquals(70, doc.minTrust)
        assertEquals("SOCCER", doc.sport)
        assertTrue(doc.deadlineAt < doc.startAt)

        host.memberRef(keoId, host.uid).delete().await()
        host.keoRef(keoId).delete().await()
        assertNull(withTimeout(TIMEOUT_MS) { b.keos.observeKeo(keoId).first { it == null } })
    }

    @Test
    fun docNhieuKeoTheoId_boQuaIdKhongTonTai() = runBlocking {
        val ids = (1..3).map { host.createKeo(title = "Kèo $it") }

        val docs = b.keos.getKeos(ids + "khong-ton-tai" + ids.first())

        assertEquals(setOf("Kèo 1", "Kèo 2", "Kèo 3"), docs.map { it.title }.toSet())
        assertEquals(3, docs.size)
        assertTrue(b.keos.getKeos(emptyList()).isEmpty())
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
