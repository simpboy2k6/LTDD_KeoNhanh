package com.keonhanh.app.ui.profile

import android.R.attr.cursorVisible
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.keonhanh.app.KeoNhanhApp
import com.keonhanh.app.R
import com.keonhanh.app.data.sample.SampleData
import com.keonhanh.app.databinding.FragmentProfileBinding
import com.keonhanh.app.util.bindAvatar
import com.keonhanh.app.util.showComingSoon
import kotlinx.coroutines.launch
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.navigation.navOptions

/** S05 Hồ sơ (TV1). Layout: res/layout/fragment_profile.xml */
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private var isEditingName = false
    // KHỞI TẠO VIEWMODEL VỚI APPCONTAINER
    private val viewModel: ProfileViewModel by viewModels {
        val appContainer = (requireActivity().application as KeoNhanhApp).container
        ProfileViewModel.ProfileViewModelFactory(
            appContainer.authRepository,
            appContainer.userRepository
        )
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentProfileBinding.bind(view)

        viewModel.fetchCurrentUserProfile();
        binding.btnEditUsername.setOnClickListener {
            if (!isEditingName) {
                // 1. CHUYỂN SANG CHẾ ĐỘ SỬA
                isEditingName = true

                binding.tvName.apply {
                    isFocusable = true
                    isFocusableInTouchMode = true
                    isCursorVisible = true
                    requestFocus()
                    setSelection(text.length) // Nhảy con trỏ xuống cuối chữ

                    // (Tùy chọn) Hiện lại gạch chân mờ mờ cho người ta biết đang nhập liệu
                    // setBackgroundResource(android.R.drawable.edit_text)
                }

                // Mở bàn phím ảo lên
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.tvName, InputMethodManager.SHOW_IMPLICIT)

                // TODO: Bạn cần tạo 1 icon dấu tích (ic_check) để thay cho cây bút
                binding.btnEditUsername.setImageResource(R.drawable.ic_lock_24) // Đổi icon thành Lưu

            } else {
                // 2. CHUYỂN VỀ CHẾ ĐỘ XEM VÀ LƯU LẠI
                isEditingName = false

                binding.tvName.apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                    isCursorVisible = false
                    clearFocus()

                    // Trả lại nền trong suốt
                    setBackgroundResource(0)
                }

                // Ẩn bàn phím ảo đi
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.tvName.windowToken, 0)

                // Trả icon về lại hình cây bút
                binding.btnEditUsername.setImageResource(R.drawable.icon_edit_name_24)

                // Lấy tên mới và gọi ViewModel lưu lên Firebase
                val newName = binding.tvName.text.toString().trim()
                viewModel.updateUsername(newName)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch{
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED){
                viewModel.changeName.collect{ message->
                    if(message!=null){
                        Toast.makeText(context, message.resolve(requireContext()), Toast.LENGTH_SHORT).show()
                        viewModel.resetChangeName()
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ProfileUiState.Loading -> {
                            binding.progressBar.visibility = View.VISIBLE
                        }
                        is ProfileUiState.Success -> {
                            binding.progressBar.visibility = View.GONE
                            val user = state.profile
                            binding.tvName.setText(user.name)
                            binding.tvEmail.text = user.email

                            binding.tvTrust.text = getString(R.string.profile_trust_value, user.trustPercent)
                            binding.progressTrust.progress = user.trustPercent
                            binding.tvTrustDetail.text = getString(R.string.profile_trust_detail, user.attended, user.committed)

                            binding.tvStatAttended.text = user.attended.toString()
                            binding.tvStatNoShow.text = user.noShows.toString()
                            binding.tvStatHosted.text = user.hosted.toString()
                        }
                        is ProfileUiState.Error -> {
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(context, state.message.resolve(requireContext()), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        binding.rowPlaces.setOnClickListener { it.showComingSoon() } // S14, P1
        binding.rowHistory.setOnClickListener { it.showComingSoon() }
        binding.btnLogout.setOnClickListener {
            viewModel.signOut()   // thêm hàm này vào ProfileViewModel: authRepository.signOut()
            findNavController().navigate(
                R.id.action_global_login, null,
                navOptions { popUpTo(R.id.nav_graph) { inclusive = true } }   // xóa back stack
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
