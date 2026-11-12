package com.keonhanh.app.ui.explore

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.keonhanh.app.R
import com.keonhanh.app.databinding.FragmentExploreMapBinding
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.appViewModels
import com.keonhanh.app.ui.common.bind
import com.keonhanh.app.ui.common.collectWhenStarted
import com.keonhanh.app.ui.lobby.LobbyFragment

/** S09 Khám phá – bản đồ (TV2). Layout: res/layout/fragment_explore_map.xml */
class ExploreMapFragment : Fragment(R.layout.fragment_explore_map) {

    private var _binding: FragmentExploreMapBinding? = null
    private val binding get() = _binding!!

    // Dùng chung ExploreViewModel để lấy danh sách từ Firestore
    private val viewModel: ExploreViewModel by appViewModels { container ->
        ExploreViewModel(container.keoRepository, container.authRepository)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentExploreMapBinding.bind(view)
        val nav = findNavController()

        binding.header.setup(
            toggleText = R.string.explore_show_list,
            toggleIcon = R.drawable.ic_format_list_bulleted_24,
            onToggle = { nav.navigate(R.id.action_exploreMap_to_exploreList) },
            onOpenFilter = { nav.navigate(R.id.action_exploreMap_to_filter) },
            onScanInvite = { nav.navigate(R.id.action_exploreMap_to_scanQr) },
            onSportChanged = { sport -> viewModel.filterBySport(sport) }
        )

        binding.fabCreate.setOnClickListener { nav.navigate(R.id.action_exploreMap_to_createKeo) }

        // Lắng nghe dữ liệu thật từ Firestore
        collectWhenStarted(viewModel.keoListState) { state ->
            when (state) {
                is UiState.Success -> {
                    val items = state.data
                    if (items.isNotEmpty()) {
                        binding.preview.root.isVisible = true

                        // Tuần 3 bản đồ là ảnh tĩnh, tạm thời luôn lấy kèo đầu tiên trong danh sách hiện lên thẻ xem trước
                        val selected = items.first()
                        binding.preview.bind(selected, showViewButton = true)

                        val openLobby = View.OnClickListener {
                            nav.navigate(
                                R.id.action_exploreMap_to_lobby,
                                bundleOf(LobbyFragment.ARG_KEO_ID to selected.id),
                            )
                        }
                        binding.preview.root.setOnClickListener(openLobby)
                        binding.preview.btnViewKeo.setOnClickListener(openLobby)
                    } else {
                        // Ẩn thẻ xem trước nếu không có kèo nào
                        binding.preview.root.isVisible = false
                    }
                }
                else -> {
                    binding.preview.root.isVisible = false
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}