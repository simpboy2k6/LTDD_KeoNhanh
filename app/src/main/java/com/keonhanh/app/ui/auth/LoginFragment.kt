package com.keonhanh.app.ui.auth

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.keonhanh.app.R
import com.keonhanh.app.databinding.FragmentLoginBinding
import com.keonhanh.app.util.showComingSoon

/** S02 Đăng nhập (TV1). Layout: res/layout/fragment_login.xml */
class LoginFragment : Fragment(R.layout.fragment_login) {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLoginBinding.bind(view)

        // Chưa đăng nhập mà bấm Back thì thoát app, không lọt vào màn Khám phá
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            requireActivity().finish()
        }

        binding.btnLogin.setOnClickListener {
            // Tuần 3: kiểm tra email trường + FirebaseAuth.signInWithEmailAndPassword rồi mới quay lại
            findNavController().popBackStack()
        }
        binding.btnRegister.setOnClickListener { it.showComingSoon() }
        binding.btnForgotPassword.setOnClickListener { it.showComingSoon() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
