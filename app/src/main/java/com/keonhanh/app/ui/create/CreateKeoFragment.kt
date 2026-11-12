package com.keonhanh.app.ui.create

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import com.keonhanh.app.R
import com.keonhanh.app.databinding.FragmentCreateKeoBinding
import com.keonhanh.app.ui.lobby.LobbyFragment
import com.keonhanh.app.util.showComingSoon

/** S12 Tạo kèo (TV2). Layout: res/layout/fragment_create_keo.xml */
class CreateKeoFragment : Fragment(R.layout.fragment_create_keo) {

    private var _binding: FragmentCreateKeoBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCreateKeoBinding.bind(view)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        // Số người: hiện giá trị cạnh tiêu đề
        updatePlayers(binding.sliderPlayers.value)
        binding.sliderPlayers.addOnChangeListener { _, value, _ -> updatePlayers(value) }

        // Ngưỡng uy tín: tắt công tắc thì ẩn thanh trượt
        updateTrust(binding.sliderTrust.value)
        binding.sliderTrust.addOnChangeListener { _, value, _ -> updateTrust(value) }
        binding.switchTrust.setOnCheckedChangeListener { _, isChecked ->
            binding.rowTrust.isVisible = isChecked
        }

        // Điểm hẹn: mở S13, nhận lại tên địa điểm qua Fragment Result
        setFragmentResultListener(PickLocationFragment.REQUEST_KEY) { _, result ->
            _binding?.etPlace?.setText(result.getString(PickLocationFragment.KEY_PLACE))
        }
        val openPicker = View.OnClickListener {
            findNavController().navigate(R.id.action_createKeo_to_pickLocation)
        }
        binding.etPlace.setOnClickListener(openPicker)
        binding.tilPlace.setEndIconOnClickListener(openPicker)

        // Bộ chọn giờ làm ở tuần 3
        binding.etTime.setOnClickListener { it.showComingSoon() }
        binding.etDeadline.setOnClickListener { it.showComingSoon() }

        binding.btnCreate.setOnClickListener {
            // Tuần 3: kiểm tra form, tính geohash, ghi Firestore rồi mở phòng chờ của kèo vừa tạo
            findNavController().navigate(
                R.id.action_createKeo_to_lobby,
                bundleOf(LobbyFragment.ARG_KEO_ID to "k3"),
            )
        }
    }

    private fun updatePlayers(value: Float) {
        binding.tvPlayersValue.text = getString(R.string.create_players_value, value.toInt())
    }

    private fun updateTrust(value: Float) {
        binding.tvTrustValue.text = getString(R.string.create_trust_value, value.toInt())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
