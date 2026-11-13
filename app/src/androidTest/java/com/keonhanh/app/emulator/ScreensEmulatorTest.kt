package com.keonhanh.app.emulator

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.keonhanh.app.MainActivity
import com.keonhanh.app.R
import com.keonhanh.app.ui.lobby.LobbyFragment
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.anything
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Mở app thật trên máy ảo (MainActivity + Fragment + ViewModel + Repository) với Firebase Emulator,
 * bấm qua S15, S16, S17 như người dùng. "me" là người đang dùng app; host và other thao tác từ "máy khác".
 * Mỗi bước chụp một ảnh vào files/test-shots của app.
 */
@RunWith(AndroidJUnit4::class)
class ScreensEmulatorTest {

    private lateinit var host: TestUser
    private lateinit var other: TestUser
    private lateinit var me: TestUser
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun signIn() = runBlocking {
        host = FirebaseEmulator.signInNew("host", "Minh")
        other = FirebaseEmulator.signInNew("c", "Chi")
        me = FirebaseEmulator.signInNew(FirebaseEmulator.APP_USER, "Hao")
    }

    @After
    fun close() {
        scenario?.close()
    }

    @Test
    fun s15_nguoiNgoai_thamGia_chat_roiKeo() = runBlocking {
        val keoId = host.createKeo(capacity = 4)
        other.members.join(keoId, other.uid, other.displayName)
        host.chat.send(keoId, host.uid, host.displayName, "Mình mang bóng nhé")

        launchAndOpenLobby(keoId)
        waitText(R.id.btn_primary, "Tham gia kèo")
        assertEquals("Đá bóng 7 người", text(R.id.tv_title))
        assertEquals("2/4", text(R.id.tv_members_count))
        assertEquals("Người ngoài không xem được chat", "Vào kèo để xem chat phòng chờ", text(R.id.tv_preview_message))
        assertTrue("Đồng hồ đếm ngược phải hiện khi kèo đang mở", visible(R.id.card_countdown))
        shot("s15_1_nguoi_ngoai")

        click(R.id.btn_primary)
        waitText(R.id.btn_primary, "Rời kèo")
        waitText(R.id.tv_members_count, "3/4")
        waitText(R.id.tv_preview_message, "Mình mang bóng nhé")
        assertEquals(3, host.memberCount(keoId))
        shot("s15_2_thanh_vien")

        // Sang S16, gửi một tin, rồi nhận một tin từ "máy khác"
        click(R.id.btn_chat)
        waitUntil("S16 hiện 1 tin có sẵn") { messageCount(it) == 1 }
        onActivity { it.findViewById<EditText>(R.id.et_message).setText("Mình tới trễ 5 phút được không?") }
        click(R.id.btn_send)
        waitUntil("Tin mình gửi hiện ra") { messageCount(it) == 2 }
        assertEquals("Ô nhập phải được xóa sau khi gửi", "", text(R.id.et_message))
        other.chat.send(keoId, other.uid, other.displayName, "Được nha, chủ kèo giữ chỗ rồi")
        waitUntil("Tin của người khác tới realtime") { messageCount(it) == 3 }
        assertEquals("Đá bóng 7 người", read { it.findViewById<MaterialToolbar>(R.id.toolbar).title?.toString() })
        shot("s16_chat")

        // Quay lại S15 và rời kèo (có hộp thoại hỏi lại)
        nav { it.popBackStack() }
        waitText(R.id.btn_primary, "Rời kèo")
        click(R.id.btn_primary)
        shot("s15_3_hop_thoai_roi_keo")
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        waitText(R.id.btn_primary, "Tham gia kèo")
        waitText(R.id.tv_members_count, "2/4")
        assertEquals(2, host.memberCount(keoId))
    }

    @Test
    fun s15_keoCoNguong_xinVao_roiDuocChuKeoDuyet() = runBlocking {
        val keoId = host.createKeo(capacity = 4, minTrust = 70)

        launchAndOpenLobby(keoId)
        waitText(R.id.btn_primary, "Xin vào")
        assertEquals("Yêu cầu uy tín ≥ 70%", text(R.id.tv_min_trust))

        click(R.id.btn_primary)
        waitText(R.id.btn_primary, "Hủy yêu cầu xin vào")
        assertEquals("Đang chờ duyệt thì chưa tính vào số người", "1/4", text(R.id.tv_members_count))
        shot("s15_4_dang_cho_duyet")

        host.members.approve(keoId, me.uid)
        waitText(R.id.btn_primary, "Rời kèo")
        waitText(R.id.tv_members_count, "2/4")
    }

    @Test
    fun s15_chuKeo_thayNutHuyKeo_vaDuyetNguoiXinVao() = runBlocking {
        val keoId = me.createKeo(capacity = 4, minTrust = 70, title = "Ma Sói tối nay")
        other.members.requestJoin(keoId, other.uid, other.displayName)

        launchAndOpenLobby(keoId)
        waitText(R.id.btn_primary, "Hủy kèo")
        waitUntil("Menu duyệt người xin vào hiện cho chủ kèo") { requestsMenuTitle(it) == "Duyệt người xin vào (1)" }
        assertTrue("Chủ kèo mời được bằng QR", read { menuVisible(it, R.id.action_invite) })
        shot("s15_5_chu_keo")

        // Mở menu "Duyệt người xin vào" -> chọn người -> bấm "Duyệt"
        onActivity { activity ->
            activity.findViewById<MaterialToolbar>(R.id.toolbar).menu.performIdentifierAction(R.id.action_requests, 0)
        }
        shot("s15_6_danh_sach_xin_vao")
        onData(anything()).inRoot(isDialog()).atPosition(0).perform(click())
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        waitText(R.id.tv_members_count, "2/4")
        waitUntil("Hết người xin vào thì menu ẩn") { !menuVisible(it, R.id.action_requests) }
        assertEquals(2, me.memberCount(keoId))

        // KeoRepository.deleteKeo còn là TODO của Huy Hoàng: bấm Hủy kèo phải báo nhẹ nhàng, không crash
        click(R.id.btn_primary)
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        waitUntil("Snackbar báo phần hủy kèo đang được làm") { snackbarText(it) == "Phần này đang được làm, thử lại sau" }
        waitText(R.id.btn_primary, "Hủy kèo")
    }

    @Test
    fun s15_daChot_thanhVienKhongConNutRoi() = runBlocking {
        val keoId = host.createKeo(capacity = 4)
        me.members.join(keoId, me.uid, me.displayName)
        host.moveDeadlineToPast(keoId)

        launchAndOpenLobby(keoId)
        waitText(R.id.btn_primary, "Đã chốt · vắng mặt tính là bùng kèo")
        assertFalse("Nút phải mờ sau hạn chốt", read { it.findViewById<View>(R.id.btn_primary).isEnabled })
        assertFalse("Hết đếm ngược sau hạn chốt", visible(R.id.card_countdown))
        shot("s15_7_da_chot")
    }

    @Test
    fun s15_trongGioCheckIn_hienTheCheckIn() = runBlocking {
        val keoId = host.createKeo(capacity = 2)
        me.members.join(keoId, me.uid, me.displayName)
        host.moveToCheckInWindow(keoId)

        launchAndOpenLobby(keoId)
        waitUntil("Thẻ check-in hiện cho thành viên") { it.findViewById<View>(R.id.tv_checkin_title)?.isShown == true }
        waitText(R.id.tv_members_count, "0/2 đã check-in")
        waitText(R.id.btn_primary, "Check-in")
        // Màn này vẽ lại mỗi giây: chờ vài nhịp để chắc không crash, không nhấp nháy trạng thái
        Thread.sleep(2_500)
        assertEquals("Check-in", text(R.id.btn_primary))
        shot("s15_8_check_in")
    }

    @Test
    fun s15_keoKhongTonTai_hienLoi_khongCrash() {
        launchAndOpenLobby("khong-co-keo-nay")
        waitUntil("Hiện trạng thái lỗi") { it.findViewById<View>(R.id.error_state)?.isVisible == true }
        assertEquals("Không mở được kèo", text(R.id.tv_empty_title))
        assertEquals("Kèo không còn tồn tại", text(R.id.tv_empty_message))
        assertFalse(visible(R.id.bar_actions))
        shot("s15_9_khong_ton_tai")
    }

    @Test
    fun chuaDangNhap_moS15vaS17_hienThongBao_khongCrash() = runBlocking {
        val keoId = host.createKeo()
        FirebaseEmulator.signedOut(FirebaseEmulator.APP_USER)

        launchAndOpenLobby(keoId)
        waitUntil("S15 hiện lỗi thay vì crash") { it.findViewById<View>(R.id.error_state)?.isVisible == true }
        assertEquals("Bạn không có quyền làm việc này", text(R.id.tv_empty_message))
        shot("s15_10_chua_dang_nhap")

        nav { it.navigate(R.id.myKeoFragment) }
        waitText(R.id.tv_empty_message, "Bạn cần đăng nhập trước")
    }

    @Test
    fun s17_keoCuaToi_baTab() = runBlocking {
        val joinedId = host.createKeo(title = "Đá bóng 7 người")
        me.members.join(joinedId, me.uid, me.displayName)
        me.createKeo(title = "Ma Sói tối nay", minTrust = 70)
        val pendingId = host.createKeo(title = "Kèo đang xin vào", minTrust = 70)
        me.members.requestJoin(pendingId, me.uid, me.displayName)

        launch()
        nav { it.navigate(R.id.myKeoFragment) }
        waitUntil("Tab Đang tham gia có 1 kèo (kèo đang xin vào không tính)") { keoCount(it) == 1 }
        assertFalse(read { it.findViewById<View>(R.id.empty).isVisible })
        waitUntil("Thẻ kèo đã vẽ xong") { firstCardText(it, R.id.tv_title) == "Đá bóng 7 người" }
        assertEquals("Chưa đo khoảng cách thì không hiện km", "Sân bóng gần cổng trường", read { firstCardText(it, R.id.tv_subtitle) })
        assertEquals("2/4 người", read { firstCardText(it, R.id.tv_slots) })
        assertEquals("Kèo 2 người mà chỉ tải dòng của mình: hiện 1 avatar và +1", "+1", read { firstCardText(it, R.id.tv_avatar_more) })
        shot("s17_1_dang_tham_gia")

        selectTab(1)
        waitUntil("Tab Đang làm chủ có 1 kèo") { keoCount(it) == 1 }
        shot("s17_2_dang_lam_chu")

        selectTab(2)
        waitText(R.id.tv_empty_message, "Kèo đã kết thúc sẽ hiện ở tab này")
        assertEquals(0, read { keoCount(it) })
        shot("s17_3_da_xong")

        // Rời kèo từ "máy khác" thì danh sách tự cập nhật
        selectTab(0)
        me.members.leave(joinedId, me.uid)
        waitText(R.id.tv_empty_message, "Kèo bạn tham gia sẽ hiện ở tab này")
    }

    // ---------- công cụ ----------

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun launchAndOpenLobby(keoId: String) {
        launch()
        nav { it.navigate(R.id.lobbyFragment, bundleOf(LobbyFragment.ARG_KEO_ID to keoId)) }
    }

    private fun onActivity(block: (MainActivity) -> Unit) = scenario!!.onActivity(block)

    private fun <T> read(block: (MainActivity) -> T): T {
        var result: T? = null
        scenario!!.onActivity { result = block(it) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun nav(block: (NavController) -> Unit) = onActivity { activity ->
        val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        block(host.navController)
    }

    private fun text(id: Int): String? = read { it.findViewById<TextView>(id)?.text?.toString() }

    private fun visible(id: Int): Boolean = read { it.findViewById<View>(id)?.isShown == true }

    private fun click(id: Int) = onActivity { it.findViewById<View>(id).performClick() }

    private fun selectTab(index: Int) = onActivity { it.findViewById<TabLayout>(R.id.tab_layout).getTabAt(index)!!.select() }

    private fun messageCount(activity: MainActivity) = activity.findViewById<RecyclerView>(R.id.rv_messages)?.adapter?.itemCount

    private fun keoCount(activity: MainActivity) = activity.findViewById<RecyclerView>(R.id.rv_keo)?.adapter?.itemCount

    /** Chữ của một view trong thẻ kèo đầu tiên ở S17; null khi thẻ chưa được vẽ. */
    private fun firstCardText(activity: MainActivity, viewId: Int): String? =
        activity.findViewById<RecyclerView>(R.id.rv_keo)?.findViewHolderForAdapterPosition(0)
            ?.itemView?.findViewById<TextView>(viewId)?.takeIf { it.isShown }?.text?.toString()

    private fun menuVisible(activity: MainActivity, itemId: Int) =
        activity.findViewById<MaterialToolbar>(R.id.toolbar)?.menu?.findItem(itemId)?.isVisible == true

    private fun requestsMenuTitle(activity: MainActivity): String? =
        activity.findViewById<MaterialToolbar>(R.id.toolbar)?.menu?.findItem(R.id.action_requests)
            ?.takeIf { it.isVisible }?.title?.toString()

    private fun snackbarText(activity: MainActivity): String? =
        activity.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.text?.toString()

    private fun waitText(id: Int, expected: String) {
        waitUntil("Chờ chữ \"$expected\", đang là \"${text(id)}\"") { it.findViewById<TextView>(id)?.text?.toString() == expected }
    }

    /** Hỏi lại màn hình mỗi 200 ms tới khi đúng điều kiện; quá giờ thì chụp ảnh và báo lỗi. */
    private fun waitUntil(what: String, timeoutMs: Long = 15_000, condition: (MainActivity) -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (read(condition)) return
            Thread.sleep(200)
        }
        shot("LOI_" + what.take(40).replace(Regex("[^\\p{L}\\p{N}]+"), "_"))
        fail("Quá ${timeoutMs / 1000} giây: $what")
    }

    private fun shot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(400)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir = File(ApplicationProvider.getApplicationContext<Context>().filesDir, "test-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
