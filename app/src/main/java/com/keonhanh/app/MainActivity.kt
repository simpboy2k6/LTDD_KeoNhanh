package com.keonhanh.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.keonhanh.app.databinding.ActivityMainBinding
import com.keonhanh.app.notify.InAppNotifier
import com.keonhanh.app.notify.NotifyStore
import com.keonhanh.app.notify.VisibleScreen
import com.keonhanh.app.ui.checkin.InviteLink
import com.keonhanh.app.ui.common.toUiText
import com.keonhanh.app.ui.lobby.LobbyFragment
import kotlinx.coroutines.launch

/**
 * Activity duy nhất của app (khung app, TV3 giữ).
 * Mọi màn hình là Fragment, nối với nhau trong res/navigation/nav_graph.xml.
 *
 * Tuần 4 (Phong Hào):
 *  - Bấm thông báo: Intent có EXTRA_KEO_ID -> mở S15 của kèo đó.
 *  - Liên kết mời keonhanh://moi/{code} (bấm link hoặc quét bằng camera của máy) -> tìm kèo theo mã -> mở S15.
 *  - Khi app hiện trên màn hình: chạy InAppNotifier để báo xin vào, được duyệt, tin nhắn mới.
 *  - Xin quyền thông báo (Android 13+) một lần, lúc vào tới các màn chính sau khi đăng nhập.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var navigationBarInset = 0

    private val container get() = (application as KeoNhanhApp).container

    /** Các màn có bottom nav: 4 tab và bottom sheet Bộ lọc (mở đè lên Khám phá). */
    private val bottomNavDestinations = setOf(
        R.id.exploreMapFragment,
        R.id.exploreListFragment,
        R.id.filterBottomSheet,
        R.id.myKeoFragment,
        R.id.chatListFragment,
        R.id.profileFragment,
    )

    // Người dùng từ chối thì app vẫn chạy, chỉ không có thông báo
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15 trở lên luôn vẽ tràn viền; tự chừa chỗ cho thanh trạng thái / điều hướng bên dưới
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            navigationBarInset = bars.bottom
            updateContentBottomPadding()
            insets
        }

        navController.addOnDestinationChangedListener { _, destination, arguments ->
            binding.bottomNav.isVisible = destination.id in bottomNavDestinations
            updateContentBottomPadding()
            useDarkSystemBars(destination.id == R.id.scanQrFragment)
            // Cho InAppNotifier biết đang ở màn nào, để không báo tin của đúng phòng chat đang mở
            VisibleScreen.destinationId = destination.id
            VisibleScreen.keoId = arguments?.getString(LobbyFragment.ARG_KEO_ID)
            if (destination.id in bottomNavDestinations) askNotificationPermissionOnce()
        }

        // Tuần 2: luôn mở Đăng nhập trước. Tuần 4: mở từ thông báo hoặc link mời khi đã đăng nhập thì vào thẳng kèo.
        if (savedInstanceState == null) {
            val opened = container.firebaseAuth.currentUser != null && openFromIntent(intent)
            if (!opened) {
                navController.navigate(R.id.action_global_login)
                if (hasOpenTarget(intent)) Toast.makeText(this, R.string.link_need_login, Toast.LENGTH_LONG).show()
            }
        }

        startInAppNotifications()
    }

    /** App đang mở mà người dùng bấm thông báo hoặc link mời: Android gửi Intent mới vào đây. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (container.firebaseAuth.currentUser != null) {
            openFromIntent(intent)
        } else if (hasOpenTarget(intent)) {
            Toast.makeText(this, R.string.link_need_login, Toast.LENGTH_LONG).show()
        }
    }

    private fun hasOpenTarget(intent: Intent?): Boolean =
        intent?.getStringExtra(EXTRA_KEO_ID) != null || InviteLink.parse(intent?.dataString) != null

    /** Mở kèo từ thông báo (EXTRA_KEO_ID) hoặc link mời (keonhanh://moi/{code}). Trả về false nếu Intent không có gì để mở. */
    private fun openFromIntent(intent: Intent?): Boolean {
        intent ?: return false
        intent.getStringExtra(EXTRA_KEO_ID)?.let { keoId ->
            intent.removeExtra(EXTRA_KEO_ID) // xoay màn hình không mở lại lần nữa
            openLobby(keoId)
            return true
        }
        val code = InviteLink.parse(intent.dataString) ?: return false
        intent.data = null
        lifecycleScope.launch {
            try {
                val keo = container.keoRepository.findByInviteCode(code)
                if (keo == null) {
                    Toast.makeText(this@MainActivity, R.string.scan_code_not_found, Toast.LENGTH_LONG).show()
                } else {
                    openLobby(keo.id)
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, e.toUiText().resolve(this@MainActivity), Toast.LENGTH_LONG).show()
            }
        }
        return true
    }

    private fun openLobby(keoId: String) {
        navController.navigate(R.id.lobbyFragment, bundleOf(LobbyFragment.ARG_KEO_ID to keoId))
    }

    /**
     * Nghe Firestore để báo ngay trong lúc app hiện trên màn hình (STARTED).
     * App rời màn hình thì dừng nghe (đỡ tốn lượt đọc) và ghi mốc; từ đó NotifyWorker lo phần báo.
     */
    private fun startInAppNotifications() {
        val notifier = InAppNotifier(
            applicationContext,
            container.firebaseAuth,
            container.memberRepository,
            container.keoRepository,
            container.chatRepository,
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                VisibleScreen.appVisible = true
                try {
                    notifier.run()
                } finally {
                    VisibleScreen.appVisible = false
                    NotifyStore(applicationContext).lastCheckMillis = System.currentTimeMillis()
                }
            }
        }
    }

    /** Android 13+ phải xin quyền thông báo lúc chạy. Chỉ hỏi một lần; từ chối thì app vẫn dùng bình thường. */
    private fun askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val store = NotifyStore(this)
        if (store.askedPermission) return
        store.askedPermission = true
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Bottom nav tự chừa chỗ cho thanh điều hướng; màn ẩn bottom nav thì vùng nội dung phải tự chừa. */
    private fun updateContentBottomPadding() {
        val bottom = if (binding.bottomNav.isVisible) 0 else navigationBarInset
        binding.navHostFragment.updatePadding(bottom = bottom)
    }

    /** Màn quét QR (S07) có nền tối: đổi nền và màu icon thanh hệ thống cho hợp. */
    private fun useDarkSystemBars(dark: Boolean) {
        val background = if (dark) R.color.kn_ink else R.color.kn_bg
        binding.root.setBackgroundColor(ContextCompat.getColor(this, background))
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    companion object {
        /** Thông báo đặt keoId vào đây; MainActivity đọc và mở S15 của kèo đó. */
        const val EXTRA_KEO_ID = "com.keonhanh.app.KEO_ID"
    }
}
