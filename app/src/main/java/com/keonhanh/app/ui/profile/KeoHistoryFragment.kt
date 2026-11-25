package com.keonhanh.app.ui.profile

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.keonhanh.app.R
import com.keonhanh.app.databinding.FragmentKeoHistoryBinding
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.appViewModels
import com.keonhanh.app.ui.lobby.LobbyFragment
import kotlinx.coroutines.launch

/** Màn Lịch sử kèo (mở từ S05 Hồ sơ). Layout: res/layout/fragment_keo_history.xml */
class KeoHistoryFragment : Fragment(R.layout.fragment_keo_history) {

    private var _binding: FragmentKeoHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel by appViewModels { container ->
        KeoHistoryViewModel(
            memberRepository = container.memberRepository,
            keoRepository = container.keoRepository,
            authRepository = container.authRepository,
        )
    }

    private lateinit var adapter: KeoHistoryAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentKeoHistoryBinding.bind(view)

        // Nút quay lại trên thanh tiêu đề
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Khởi tạo Adapter cho RecyclerView
        adapter = KeoHistoryAdapter { item ->
            findNavController().navigate(
                R.id.action_keoHistory_to_lobby,
                bundleOf(LobbyFragment.ARG_KEO_ID to item.keo.id),
            )
        }
        binding.rvHistory.adapter = adapter

        // Lắng nghe dữ liệu từ ViewModel và vẽ lên giao diện
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    showState(state)
                }
            }
        }
    }

    private fun showState(state: UiState<KeoHistoryUi>) {
        binding.progressLoading.isVisible = state is UiState.Loading

        when (state) {
            is UiState.Loading -> {
                adapter.submitList(emptyList())
                binding.empty.root.isVisible = false
            }
            is UiState.Error -> {
                adapter.submitList(emptyList())
                binding.tvStatTotal.text = "0"
                binding.tvStatAttended.text = "0"
                binding.tvStatAbsent.text = "0"

                binding.empty.root.isVisible = true
                binding.empty.ivEmptyIcon.setImageResource(R.drawable.ic_error_24)
                binding.empty.tvEmptyTitle.setText(R.string.history_error_title)
                binding.empty.tvEmptyMessage.text = state.message.resolve(requireContext())
            }
            is UiState.Success -> {
                val data = state.data
                binding.tvStatTotal.text = data.totalCount.toString()
                binding.tvStatAttended.text = data.attendedCount.toString()
                binding.tvStatAbsent.text = data.absentCount.toString()

                adapter.submitList(data.items)

                if (data.items.isEmpty()) {
                    binding.empty.root.isVisible = true
                    binding.empty.ivEmptyIcon.setImageResource(R.drawable.ic_history_24)
                    binding.empty.tvEmptyTitle.setText(R.string.history_empty_title)
                    binding.empty.tvEmptyMessage.setText(R.string.history_empty_message)
                } else {
                    binding.empty.root.isVisible = false
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
