package com.keonhanh.app.ui.explore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        binding.sliderRadius.value = DEFAULT_RADIUS_KM.toFloat()
        binding.sliderRadius.setLabelFormatter { value -> radiusText(value) }
        binding.tvRadiusValue.text = radiusText(binding.sliderRadius.value)
        binding.sliderRadius.addOnChangeListener { _, value, _ ->
            binding.tvRadiusValue.text = radiusText(value)
        }

        binding.btnReset.setOnClickListener {
            binding.chipGroupSport.clearCheck()
            binding.sliderRadius.value = DEFAULT_RADIUS_KM.toFloat()
        }
        // Tuần 3: gửi môn + bán kính về ViewModel dùng chung rồi mới đóng
        binding.btnApply.setOnClickListener { dismiss() }
    }

    private fun radiusText(value: Float) = getString(R.string.filter_radius_value, value.toInt())

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
