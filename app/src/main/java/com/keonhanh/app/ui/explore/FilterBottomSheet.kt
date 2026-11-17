package com.keonhanh.app.ui.explore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.keonhanh.app.R
import com.keonhanh.app.databinding.BottomSheetFilterBinding
import com.keonhanh.app.ui.common.appViewModels // Bắt buộc import hàm này

/** S11 Bộ lọc (TV2). Layout: res/layout/bottom_sheet_filter.xml */
class FilterBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetFilterBinding? = null
    private val binding get() = _binding!!

    // KHỞI TẠO VIEWMODEL DÙNG CHUNG
    // Dùng appViewModels để lấy đúng instance đang giữ state filterFlow của màn hình danh sách
    private val viewModel: ExploreViewModel by appViewModels { container ->
        ExploreViewModel(
            authRepository = container.authRepository,
            keoRepository = container.keoRepository,
            locationRepository = container.locationRepository
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Phục hồi trạng thái cũ của bộ lọc từ ViewModel lên giao diện
        val currentFilter = viewModel.filterFlow.value
        binding.sliderRadius.value = currentFilter.radiusKm.toFloat()

        when (currentFilter.sport) {
            "SOCCER" -> binding.chipGroupSport.check(R.id.chip_soccer)
            "BADMINTON" -> binding.chipGroupSport.check(R.id.chip_badminton)
            "BOARD_GAME" -> binding.chipGroupSport.check(R.id.chip_board_game)
            else -> binding.chipGroupSport.clearCheck()
        }

        when (currentFilter.timeRange) {
            "Hôm nay" -> binding.chipGroupTime.check(R.id.chip_today)
            "Ngày mai" -> binding.chipGroupTime.check(R.id.chip_tomorrow)
            "Cuối tuần" -> binding.chipGroupTime.check(R.id.chip_weekend)
            else -> binding.chipGroupTime.clearCheck()
        }

        // 2. Thiết lập hiển thị Slider
        binding.sliderRadius.setLabelFormatter { value -> radiusText(value) }
        binding.tvRadiusValue.text = radiusText(binding.sliderRadius.value)
        binding.sliderRadius.addOnChangeListener { _, value, _ ->
            binding.tvRadiusValue.text = radiusText(value)
        }

        // 3. Xử lý nút Đặt lại
        binding.btnReset.setOnClickListener {
            binding.chipGroupSport.clearCheck()
            binding.chipGroupTime.clearCheck() // Thêm lệnh clear thời gian
            binding.sliderRadius.value = DEFAULT_RADIUS_KM.toFloat()
        }

        // 4. Xử lý nút Áp dụng
        binding.btnApply.setOnClickListener {
            val selectedRadius = binding.sliderRadius.value // Giữ nguyên kiểu Float

            val selectedSport = when (binding.chipGroupSport.checkedChipId) {
                R.id.chip_soccer -> "SOCCER"
                R.id.chip_badminton -> "BADMINTON"
                R.id.chip_board_game -> "BOARD_GAME"
                else -> ""
            }

            val selectedTime = when (binding.chipGroupTime.checkedChipId) {
                R.id.chip_today -> "Hôm nay"
                R.id.chip_tomorrow -> "Ngày mai"
                R.id.chip_weekend -> "Cuối tuần"
                else -> ""
            }

            // Ghi đè filter cũ và cập nhật
            viewModel.updateFilter(
                currentFilter.copy(
                    sport = selectedSport,
                    radiusKm = selectedRadius,
                    timeRange = selectedTime
                )
            )

            dismiss()
        }
    }

    private fun radiusText(value: Float) = getString(R.string.filter_radius_value, value.toInt())

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val DEFAULT_RADIUS_KM = 3 // Khai báo biến default nếu chưa có
    }
}