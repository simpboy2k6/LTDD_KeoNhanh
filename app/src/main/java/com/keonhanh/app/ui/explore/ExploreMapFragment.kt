package com.keonhanh.app.ui.explore

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResultListener
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
        ExploreViewModel(
            authRepository = container.authRepository,
            keoRepository = container.keoRepository,
            locationRepository = container.locationRepository
        )
    }
    private var hasAskedLocationThisSession = false

    private val locationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.refreshLocation()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentExploreMapBinding.bind(view)
        val nav = findNavController()

        setFragmentResultListener(FilterBottomSheet.REQUEST_KEY) { _, b ->
            val sport = b.getString(FilterBottomSheet.KEY_SPORT).orEmpty()
            val radius = b.getFloat(FilterBottomSheet.KEY_RADIUS, FilterBottomSheet.DEFAULT_RADIUS_KM)
            val time = b.getString(FilterBottomSheet.KEY_TIME).orEmpty()

            val currentFilter = viewModel.filterFlow.value
            viewModel.updateFilter(
                currentFilter.copy(
                    sport = sport,
                    radiusKm = radius,
                    timeRange = time
                )
            )
        }

        binding.header.setup(
            toggleText = R.string.explore_show_list,
            toggleIcon = R.drawable.ic_format_list_bulleted_24,
            onToggle = { nav.navigate(R.id.action_exploreMap_to_exploreList) },
            onOpenFilter = {
                val f = viewModel.filterFlow.value
                nav.navigate(
                    R.id.action_exploreMap_to_filter,
                    bundleOf(
                        FilterBottomSheet.KEY_SPORT to f.sport,
                        FilterBottomSheet.KEY_RADIUS to f.radiusKm,
                        FilterBottomSheet.KEY_TIME to f.timeRange
                    )
                )
            },
            onScanInvite = { nav.navigate(R.id.action_exploreMap_to_scanQr) },
            onSportChanged = { sport ->
                val currentFilter = viewModel.filterFlow.value
                viewModel.updateFilter(currentFilter.copy(sport = sport?.name ?: ""))
            }
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
        collectWhenStarted(viewModel.filterFlow) { filter ->
            binding.header.updateRadius(filter.radiusKm.toInt())
        }
        val container = (requireActivity().application as com.keonhanh.app.KeoNhanhApp).container
        if (!container.locationRepository.hasPermission() && !hasAskedLocationThisSession) {
            hasAskedLocationThisSession = true
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshLocation()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
