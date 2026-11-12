package com.keonhanh.app.ui.profile

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

/** S05 Hồ sơ (TV1). Layout: res/layout/fragment_profile.xml */
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ProfileUiState.Loading -> {
                            // TODO: Hiển thị ProgressBar (nếu có)
                        }
                        is ProfileUiState.Success -> {
                            val user = state.profile
                            binding.tvName.text = user.name
                            binding.tvEmail.text = user.email

                            binding.tvTrust.text = getString(R.string.profile_trust_value, user.trustPercent)
                            binding.progressTrust.progress = user.trustPercent
                            binding.tvTrustDetail.text = getString(R.string.profile_trust_detail, user.attended, user.committed)

                            binding.tvStatAttended.text = user.attended.toString()
                            binding.tvStatNoShow.text = user.noShows.toString()
                            binding.tvStatHosted.text = user.hosted.toString()
                        }
                        is ProfileUiState.Error -> {
                            Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        binding.rowPlaces.setOnClickListener { it.showComingSoon() } // S14, P1
        binding.rowHistory.setOnClickListener { it.showComingSoon() }
        binding.btnLogout.setOnClickListener {
            findNavController().navigate(R.id.action_global_login)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
