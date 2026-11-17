package com.keonhanh.app.ui.explore

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.keonhanh.app.R
import com.keonhanh.app.data.model.Sport
import com.keonhanh.app.databinding.FragmentExploreListBinding
import com.keonhanh.app.ui.common.KeoAdapter
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.appViewModels
import com.keonhanh.app.ui.common.collectWhenStarted
import com.keonhanh.app.ui.lobby.LobbyFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ExploreListFragment : Fragment(R.layout.fragment_explore_list) {

    private var _binding: FragmentExploreListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: KeoAdapter
    private var searchJob: Job? = null // Thêm biến lưu giữ tiến trình đếm ngược

    // Sửa lỗi khởi tạo ViewModel: Cần truyền thêm locationRepository
    private val viewModel: ExploreViewModel by appViewModels { container ->
        ExploreViewModel(
            authRepository = container.authRepository,
            keoRepository = container.keoRepository,
            locationRepository = container.locationRepository // Truyền thêm biến này
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentExploreListBinding.bind(view)
        val nav = findNavController()

        adapter = KeoAdapter { keo ->
            nav.navigate(R.id.action_exploreList_to_lobby, bundleOf(LobbyFragment.ARG_KEO_ID to keo.id))
        }
        binding.rvKeo.adapter = adapter

        binding.empty.ivEmptyIcon.setImageResource(R.drawable.ic_search_24)
        binding.empty.tvEmptyTitle.setText(R.string.explore_empty_title)
        binding.empty.tvEmptyMessage.setText(R.string.explore_empty_message)

        // Sửa lỗi logic cập nhật môn học (Gộp vào updateFilter)
        binding.header.setup(
            toggleText = R.string.explore_show_map,
            toggleIcon = R.drawable.ic_map_24,
            onToggle = { nav.popBackStack() },
            onOpenFilter = { nav.navigate(R.id.action_exploreList_to_filter) },
            onScanInvite = { nav.navigate(R.id.action_exploreList_to_scanQr) },
            onSportChanged = { sport ->
                val currentFilter = viewModel.filterFlow.value
                viewModel.updateFilter(currentFilter.copy(sport = sport?.name ?: ""))
            },
        )
        binding.fabCreate.setOnClickListener { nav.navigate(R.id.action_exploreList_to_createKeo) }

        // --- PHẦN MỚI: LẮNG NGHE GÕ TÌM KIẾM CÓ ĐỘ TRỄ 300MS ---
        // Giả sử ID của EditText trong view_explore_header.xml là tv_search_hint
        binding.header.tvSearchHint.doAfterTextChanged { editable ->
            val text = editable?.toString() ?: ""

            searchJob?.cancel() // Hủy bộ đếm cũ nếu người dùng đang gõ tiếp

            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(300) // Đợi 300ms sau khi ngừng gõ
                val currentFilter = viewModel.filterFlow.value
                viewModel.updateFilter(currentFilter.copy(query = text))
            }
        }
        // ---------------------------------------------------------

        collectWhenStarted(viewModel.keoListState) { state ->
            when (state) {
                is UiState.Loading -> {
                    binding.empty.root.isVisible = false
                }
                is UiState.Success -> {
                    val items = state.data
                    adapter.submitList(items)
                    binding.empty.root.isVisible = items.isEmpty()
                }
                is UiState.Error -> {
                    binding.empty.root.isVisible = true
                    binding.empty.tvEmptyTitle.text = "Lỗi tải dữ liệu"
                    binding.empty.tvEmptyMessage.text = state.message.resolve(requireContext())
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}