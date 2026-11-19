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
import androidx.fragment.app.setFragmentResultListener
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
    private val locationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.refreshLocation()
        }
    }
    private var hasAskedLocationThisSession = false

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
        // Sửa lỗi logic cập nhật môn học (Gộp vào updateFilter)
        binding.header.setup(
            toggleText = R.string.explore_show_map,
            toggleIcon = R.drawable.ic_map_24,
            onToggle = { nav.popBackStack() },
            onOpenFilter = {
                val f = viewModel.filterFlow.value
                nav.navigate(
                    R.id.action_exploreList_to_filter,
                    bundleOf(
                        FilterBottomSheet.KEY_SPORT to f.sport,
                        FilterBottomSheet.KEY_RADIUS to f.radiusKm,
                        FilterBottomSheet.KEY_TIME to f.timeRange
                    )
                )
            },
            onScanInvite = { nav.navigate(R.id.action_exploreList_to_scanQr) },
            onSportChanged = { sport ->
                val currentFilter = viewModel.filterFlow.value
                viewModel.updateFilter(currentFilter.copy(sport = sport?.name ?: ""))
            },
            onSearchChanged = { query ->
                val currentFilter = viewModel.filterFlow.value
                viewModel.updateFilter(currentFilter.copy(query = query))
            }
        )
        binding.fabCreate.setOnClickListener { nav.navigate(R.id.action_exploreList_to_createKeo) }

        // --- PHẦN MỚI: LẮNG NGHE GÕ TÌM KIẾM CÓ ĐỘ TRỄ 300MS ---

        // ---------------------------------------------------------

        collectWhenStarted(viewModel.keoListState) { state ->
            when (state) {
                is UiState.Loading -> {
                    binding.empty.root.isVisible = false
                }
                is UiState.Success -> {
                    val items = state.data
                    adapter.submitList(items)
                    if (items.isEmpty()) {
                        binding.empty.tvEmptyTitle.setText(R.string.explore_empty_title)
                        binding.empty.tvEmptyMessage.setText(R.string.explore_empty_message)
                    }
                    binding.empty.root.isVisible = items.isEmpty()
                }
                is UiState.Error -> {
                    binding.empty.root.isVisible = true
                    binding.empty.tvEmptyTitle.text = "Lỗi tải dữ liệu"
                    binding.empty.tvEmptyMessage.text = state.message.resolve(requireContext())
                }
            }
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
        collectWhenStarted(viewModel.filterFlow) { filter ->
            binding.header.updateRadius(filter.radiusKm.toInt())
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