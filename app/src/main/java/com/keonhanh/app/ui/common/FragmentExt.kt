package com.keonhanh.app.ui.common

import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.keonhanh.app.KeoNhanhApp
import com.keonhanh.app.di.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** AppContainer duy nhất của app (tạo một lần trong KeoNhanhApp.onCreate). */
val Fragment.appContainer: AppContainer
    get() = (requireActivity().application as KeoNhanhApp).container

/**
 * Tạo ViewModel có tham số (Repository) mà không phải viết class Factory riêng cho từng màn.
 * ViewModel sống qua lúc xoay màn hình; Fragment tạo lại thì vẫn lấy đúng ViewModel cũ.
 *
 * Cách dùng trong Fragment:
 *
 *   private val viewModel by appViewModels { container ->
 *       ExploreViewModel(container.keoRepository)
 *   }
 */
inline fun <reified VM : ViewModel> Fragment.appViewModels(
    noinline create: (AppContainer) -> VM,
): Lazy<VM> = viewModels {
    viewModelFactory { initializer { create(appContainer) } }
}

/**
 * Thu thập một Flow của ViewModel trong lúc màn hình đang hiện (STARTED), tự dừng khi ẩn màn hoặc hủy view.
 * Gọi trong onViewCreated:
 *
 *   collectWhenStarted(viewModel.state) { state -> render(state) }
 */
fun <T> Fragment.collectWhenStarted(flow: Flow<T>, action: suspend (T) -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { action(it) }
        }
    }
}
