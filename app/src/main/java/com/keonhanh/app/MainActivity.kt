package com.keonhanh.app

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.keonhanh.app.databinding.ActivityMainBinding

/**
 * Activity duy nhất của app (khung app, TV3 giữ).
 * Mọi màn hình là Fragment, nối với nhau trong res/navigation/nav_graph.xml.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var navigationBarInset = 0

    /** Các màn có bottom nav: 4 tab và bottom sheet Bộ lọc (mở đè lên Khám phá). */
    private val bottomNavDestinations = setOf(
        R.id.exploreMapFragment,
        R.id.exploreListFragment,
        R.id.filterBottomSheet,
        R.id.myKeoFragment,
        R.id.chatListFragment,
        R.id.profileFragment,
    )

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

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.isVisible = destination.id in bottomNavDestinations
            updateContentBottomPadding()
            useDarkSystemBars(destination.id == R.id.scanQrFragment)
        }

        // Tuần 2: luôn mở Đăng nhập trước. Tuần 3: chỉ mở khi FirebaseAuth chưa có người dùng.
        if (savedInstanceState == null) {
            navController.navigate(R.id.action_global_login)
        }
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
}
