package com.keonhanh.app.ui.create

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.keonhanh.app.R
import com.keonhanh.app.data.model.Sport
import com.keonhanh.app.databinding.FragmentCreateKeoBinding
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.appViewModels
import com.keonhanh.app.ui.lobby.LobbyFragment
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** S12 Tạo kèo (TV2). Layout: res/layout/fragment_create_keo.xml */
class CreateKeoFragment : Fragment(R.layout.fragment_create_keo) {

    private var _binding: FragmentCreateKeoBinding? = null
    private val binding get() = _binding!!

    // Khởi tạo ViewModel bằng hàm appViewModels của TV3
    private val viewModel: CreateKeoViewModel by appViewModels { container ->
        CreateKeoViewModel(container.keoRepository, container.authRepository)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCreateKeoBinding.bind(view)

        // Đã sửa lỗi chính tả setNavigationOnClickListener
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

        // Ẩn ô Giờ hẹn trên giao diện (chỉ dùng code)
        binding.etTime.visibility = View.GONE

        // Mở bộ chọn ngày giờ khi bấm vào Hạn chốt
        binding.etDeadline.setOnClickListener { showDateTimePicker(binding.etDeadline) }

        // Logic xử lý khi bấm nút Tạo Kèo
        binding.btnCreate.setOnClickListener {
            val title = binding.etName.text.toString()
            val place = binding.etPlace.text.toString()
            val capacity = binding.sliderPlayers.value.toInt()
            val minTrust = if (binding.switchTrust.isChecked) binding.sliderTrust.value.toInt() else null

            // Hardcode tọa độ tạm thời
            val mockLat = 16.0748
            val mockLng = 108.1512

            viewModel.createKeo(
                title = title,
                sport = Sport.SOCCER,
                placeName = place,
                lat = mockLat,
                lng = mockLng,
                // Không truyền startAtString nữa
                deadlineAtString = binding.etDeadline.text.toString(),
                capacity = capacity,
                minTrust = minTrust
            )
        }

        // Lắng nghe trạng thái lưu dữ liệu để phản hồi UI
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.createState.collect { state ->
                when (state) {
                    is UiState.Loading -> {
                        binding.btnCreate.isEnabled = false
                        binding.btnCreate.setText(R.string.create_submit)
                    }
                    is UiState.Success -> {
                        binding.btnCreate.isEnabled = true
                        findNavController().navigate(
                            R.id.action_createKeo_to_lobby,
                            bundleOf(LobbyFragment.ARG_KEO_ID to state.data)
                        )
                    }
                    is UiState.Error -> {
                        binding.btnCreate.isEnabled = true
                        Snackbar.make(requireView(), state.message.resolve(requireContext()), Snackbar.LENGTH_LONG).show()
                    }
                    null -> {
                        binding.btnCreate.isEnabled = true
                        binding.btnCreate.setText(R.string.create_submit)
                    }
                }
            }
        }
    }

    private fun updatePlayers(value: Float) {
        binding.tvPlayersValue.text = getString(R.string.create_players_value, value.toInt())
    }

    private fun updateTrust(value: Float) {
        binding.tvTrustValue.text = getString(R.string.create_trust_value, value.toInt())
    }

    // Hàm hiển thị hộp thoại chọn ngày và giờ chuẩn của Android
    private fun showDateTimePicker(editText: EditText) {
        val calendar = Calendar.getInstance()

        DatePickerDialog(requireContext(), { _, year, month, day ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, day)

            TimePickerDialog(requireContext(), { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)

                val format = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                editText.setText(format.format(calendar.time))

            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()

        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}