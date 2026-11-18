package com.keonhanh.app.ui.explore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.keonhanh.app.R
import com.keonhanh.app.databinding.BottomSheetFilterBinding

/** S11 Bộ lọc (TV2). Layout: res/layout/bottom_sheet_filter.xml */
class FilterBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetFilterBinding? = null
    private val binding get() = _binding!!

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

        // Nhận lại trạng thái cũ nếu màn hình cha truyền sang qua arguments
        val initSport = arguments?.getString(KEY_SPORT).orEmpty()
        val initRadius = arguments?.getFloat(KEY_RADIUS, DEFAULT_RADIUS_KM) ?: DEFAULT_RADIUS_KM
        val initTime = arguments?.getString(KEY_TIME).orEmpty()

        binding.sliderRadius.value = initRadius

        when (initSport) {
            "SOCCER" -> binding.chipGroupSport.check(R.id.chip_soccer)
            "BADMINTON" -> binding.chipGroupSport.check(R.id.chip_badminton)
            "BOARD_GAME" -> binding.chipGroupSport.check(R.id.chip_board_game)
            else -> binding.chipGroupSport.clearCheck()
        }

        when (initTime) {
            "Hôm nay" -> binding.chipGroupTime.check(R.id.chip_today)
            "Ngày mai" -> binding.chipGroupTime.check(R.id.chip_tomorrow)
            "Cuối tuần" -> binding.chipGroupTime.check(R.id.chip_weekend)
            else -> binding.chipGroupTime.clearCheck()
        }

        binding.sliderRadius.setLabelFormatter { value -> radiusText(value) }
        binding.tvRadiusValue.text = radiusText(binding.sliderRadius.value)
        binding.sliderRadius.addOnChangeListener { _, value, _ ->
            binding.tvRadiusValue.text = radiusText(value)
        }

        binding.btnReset.setOnClickListener {
            binding.chipGroupSport.clearCheck()
            binding.chipGroupTime.clearCheck()
            binding.sliderRadius.value = DEFAULT_RADIUS_KM
        }

        binding.btnApply.setOnClickListener {
            val selectedRadius = binding.sliderRadius.value

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

            setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    KEY_SPORT to selectedSport,
                    KEY_RADIUS to selectedRadius,
                    KEY_TIME to selectedTime
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
        const val REQUEST_KEY = "filter"
        const val KEY_SPORT = "sport"
        const val KEY_RADIUS = "radius"
        const val KEY_TIME = "time"
        const val DEFAULT_RADIUS_KM = 3f
    }
}