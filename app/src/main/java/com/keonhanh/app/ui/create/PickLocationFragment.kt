package com.keonhanh.app.ui.create

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import com.keonhanh.app.R
import com.keonhanh.app.data.model.CheckInRules
import com.keonhanh.app.data.sample.SampleData
import com.keonhanh.app.databinding.FragmentPickLocationBinding
import com.keonhanh.app.util.showComingSoon

/** S13 Chọn điểm hẹn (TV2). Layout: res/layout/fragment_pick_location.xml */
class PickLocationFragment : Fragment(R.layout.fragment_pick_location) {

    private var _binding: FragmentPickLocationBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPickLocationBinding.bind(view)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.tvPlace.text = SampleData.PICKED_PLACE
        binding.tvRadiusNote.text = getString(R.string.pick_radius_note, CheckInRules.RADIUS_M)
        binding.fabMyLocation.setOnClickListener { it.showComingSoon() }

        binding.btnConfirm.setOnClickListener {
            // Tuần 3: gửi kèm vĩ độ / kinh độ tâm bản đồ (GeoPoint) để tính geohash và check-in
            setFragmentResult(REQUEST_KEY, bundleOf(KEY_PLACE to SampleData.PICKED_PLACE))
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val REQUEST_KEY = "pick_location"
        const val KEY_PLACE = "place"
    }
}
