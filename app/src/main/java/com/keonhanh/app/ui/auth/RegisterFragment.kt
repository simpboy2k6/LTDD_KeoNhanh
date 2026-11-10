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
            val email = binding.etEmail.text.toString()
            val pass = binding.etPassword.text.toString()
            val confirmPass = binding.etConfirmPassword.text.toString()

            if (pass != confirmPass) {
                Toast.makeText(context, "Mật khẩu xác nhận không khớp!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val name = if (email.contains("@")) email.substringBefore("@") else "User"

            viewModel.register(email, pass, name)
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
                        is AuthUiState.Loading -> { /* Hiện vòng xoay Loading */ }
                        is AuthUiState.Success -> {
                            Toast.makeText(context, "Đăng ký thành công!", Toast.LENGTH_SHORT).show()
                            findNavController().navigate(R.id.action_register_to_explore_graph)
                        }
                        is AuthUiState.Error -> {
                            Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                            viewModel.resetState() // Tránh báo lỗi lại khi xoay màn hình
                        }
                    }
                }
            }
        }
    }
}