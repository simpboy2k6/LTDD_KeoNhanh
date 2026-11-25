package com.keonhanh.app.ui.auth

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
import com.keonhanh.app.databinding.FragmentRegisterBinding
import com.keonhanh.app.util.hasInternetConnection
import kotlinx.coroutines.launch


class RegisterFragment : Fragment(R.layout.fragment_register) {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels {
        // Lấy container từ KeoNhanhApp
        val appContainer = (requireActivity().application as KeoNhanhApp).container

        // Gọi Factory để tạo ViewModel với 2 Repository đã cấu hình sẵn
        AuthViewModelFactory(
            appContainer.authRepository,
            appContainer.userRepository
        )

    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentRegisterBinding.bind(view)
        // Ví dụ khi người dùng bấm nút Đăng ký
        binding.btnRegister.setOnClickListener {
            val fullName = binding.etFullName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()
            val confirmPassword = binding.etConfirmPassword.text.toString()

            // 1. XÓA TẤT CẢ LỖI CŨ (Reset lại trạng thái bình thường)
            binding.tilFullName.error = null
            binding.tilEmail.error = null
            binding.tilPassword.error = null
            binding.tilConfirmPassword.error = null

            // 2. KIỂM TRA TỪNG ĐIỀU KIỆN VÀ BÁO LỖI VÀO TỪNG Ô
            var isValid = true
            if (fullName.isEmpty()) {
                binding.tilFullName.error = getString(R.string.error_name_required)
                isValid = false
            } else if (fullName.length !in 2..40) {
                binding.tilFullName.error = getString(R.string.error_name_length)
                isValid = false
            }

            if (email.isEmpty()) {
                binding.tilEmail.error = getString(R.string.error_email_required) // Hiện chữ đỏ dưới ô Email
                isValid = false
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilEmail.error = getString(R.string.error_email_invalid)
                isValid = false
            }

            if (password.any { it.isWhitespace() }) {
                binding.tilPassword.error = getString(R.string.error_password_whitespace)
                isValid = false
            } else if (password.length < 6) {
                binding.tilPassword.error = getString(R.string.error_password_min_length) // Hiện chữ đỏ dưới ô Mật khẩu
                isValid = false
            }

            if (confirmPassword != password) {
                binding.tilConfirmPassword.error = getString(R.string.error_confirm_password_mismatch) // Hiện chữ đỏ dưới ô Xác nhận
                isValid = false
            }

            // 3. NẾU MỌI THỨ ĐỀU ĐÚNG VÀ CÓ MẠNG THÌ MỚI GỌI VIEWMODEL ĐỂ ĐĂNG KÝ
            if (isValid) {
                if (!requireContext().hasInternetConnection()) {
                    Toast.makeText(context, getString(R.string.err_network_request_failed), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                // Đây chính là phần "truyền tên thật" mà yêu cầu nhắc tới
                viewModel.register(email, password, fullName)
            }
        }
        binding.btnGoToLogin.setOnClickListener {
            // Gọi action quay lại màn Đăng nhập
            findNavController().navigate(R.id.action_register_to_login)
        }
        // Lắng nghe trạng thái trả về để update giao diện
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is AuthUiState.Idle -> { /* Ẩn loading */ }
                        is AuthUiState.Loading -> {
                            binding.btnRegister.isEnabled = false
                            binding.btnRegister.setText(R.string.register_button_loading)
                        }
                        is AuthUiState.Success -> {
                            binding.btnRegister.isEnabled = true
                            binding.btnRegister.setText(R.string.login_register)
                            Toast.makeText(context, getString(R.string.register_success), Toast.LENGTH_SHORT).show()
                            findNavController().navigate(R.id.action_register_to_login)
                        }
                        is AuthUiState.Error -> {
                            binding.btnRegister.isEnabled = true
                            binding.btnRegister.setText(R.string.login_register)
                            Toast.makeText(context, getString(state.IdMessage), Toast.LENGTH_SHORT).show()
                            viewModel.resetState() // Tránh báo lỗi lại khi xoay màn hình
                        }
                    }
                }
            }
        }
    }
}