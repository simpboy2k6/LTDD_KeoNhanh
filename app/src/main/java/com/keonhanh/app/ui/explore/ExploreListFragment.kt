package com.keonhanh.app.ui.explore

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.keonhanh.app.R
import com.keonhanh.app.databinding.FragmentExploreListBinding
import com.keonhanh.app.ui.common.KeoAdapter
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.appViewModels
import com.keonhanh.app.ui.common.collectWhenStarted
import com.keonhanh.app.ui.lobby.LobbyFragment

/** S10 Khám phá – danh sách (TV2). Layout: res/layout/fragment_explore_list.xml */
class ExploreListFragment : Fragment(R.layout.fragment_explore_list) {

    private var _binding: FragmentExploreListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: KeoAdapter

    // Lấy ExploreViewModel
    private val viewModel: ExploreViewModel by appViewModels { container ->
        ExploreViewModel(container.keoRepository, container.authRepository)
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

        binding.header.setup(
            toggleText = R.string.explore_show_map,
            toggleIcon = R.drawable.ic_map_24,
            onToggle = { nav.popBackStack() },
            onOpenFilter = { nav.navigate(R.id.action_exploreList_to_filter) },
            onScanInvite = { nav.navigate(R.id.action_exploreList_to_scanQr) },
            onSportChanged = { sport -> viewModel.filterBySport(sport) }, // Truyền sự kiện xuống ViewModel
        )
        binding.fabCreate.setOnClickListener { nav.navigate(R.id.action_exploreList_to_createKeo) }

        // Bắt đầu lắng nghe sự thay đổi của danh sách kèo từ Firestore
        collectWhenStarted(viewModel.keoListState) { state ->
            when (state) {
                is UiState.Loading -> {
                    binding.empty.root.isVisible = false
                    // Nếu sau này bạn có thêm ProgressBar thì bật nó ở đây
                }
                is UiState.Success -> {
                    val items = state.data
                    adapter.submitList(items)
                    binding.empty.root.isVisible = items.isEmpty()
                }
                is UiState.Error -> {
                    // Bạn có thể show Snackbar báo lỗi tại đây
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
