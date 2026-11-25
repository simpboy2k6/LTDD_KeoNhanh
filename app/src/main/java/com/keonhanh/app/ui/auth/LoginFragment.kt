package com.keonhanh.app.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.keonhanh.app.KeoNhanhApp
import com.keonhanh.app.R
import com.keonhanh.app.databinding.FragmentLoginBinding
import com.keonhanh.app.util.showComingSoon
import kotlinx.coroutines.launch

/** S02 Đăng nhập (TV1). Layout: res/layout/fragment_login.xml */
class LoginFragment : Fragment(R.layout.fragment_login) {

    private var _binding: FragmentLoginBinding? = null
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
        _binding = FragmentLoginBinding.bind(view)

        // Chưa đăng nhập mà bấm Back thì thoát app, không lọt vào màn Khám phá
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            requireActivity().finish()
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val pass = binding.etPassword.text.toString()

            viewModel.login(email,pass);


        }
        binding.btnRegister.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }
        binding.btnForgotPassword.setOnClickListener {
            val email = binding.etEmail.text.toString()
            // Dùng TextInputLayout.error để báo lỗi nếu chưa nhập email
            if (email.isEmpty()) {
                binding.tilEmail.error = getString(R.string.login_error_email_first)
            } else {
                binding.tilEmail.error = null
                viewModel.sendPasswordReset(email)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED){
                viewModel.resetPasswordMessage.collect {message ->
                    if (message != null) {
                        Toast.makeText(context, message.resolve(requireContext()), Toast.LENGTH_LONG).show()
                        viewModel.clearResetPasswordMessage() // Xóa đi để không bị hiện lại khi xoay màn hình
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is AuthUiState.Idle -> { /* Ẩn loading */ }
                        is AuthUiState.Loading -> {

                            binding.btnLogin.isEnabled = false
                            binding.btnLogin.setText(R.string.login_loading)
                        }
                        is AuthUiState.Success -> {
                            binding.btnLogin.isEnabled = true
                            binding.btnLogin.setText(R.string.login_button)
                            Toast.makeText(context, getString(R.string.login_success), Toast.LENGTH_SHORT).show()
                            findNavController().navigate(R.id.action_login_to_explore_graph)
                        }
                        is AuthUiState.Error -> {
                            binding.btnLogin.isEnabled = true
                            binding.btnLogin.setText(R.string.login_button)
                            Toast.makeText(context, getString(state.IdMessage), Toast.LENGTH_SHORT).show()
                            viewModel.resetState() // Tránh báo lỗi lại khi xoay màn hình
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}