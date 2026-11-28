package com.keonhanh.app.ui.create

import com.keonhanh.app.util.hasInternetConnection
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
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

    private var pickedLat: Double? = null
    private var pickedLng: Double? = null

    private val viewModel: CreateKeoViewModel by appViewModels { container ->
        CreateKeoViewModel(
            keoRepository = container.keoRepository,
            authRepository = container.authRepository,
            userRepository = container.userRepository
        )
    }

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

        // Xóa lỗi dưới ô nhập khi người dùng sửa lại dữ liệu
        binding.etName.doAfterTextChanged { binding.tilName.error = null }

        // Điểm hẹn: mở S13, nhận lại cả tên, lat, lng qua Fragment Result
        setFragmentResultListener(PickLocationFragment.REQUEST_KEY) { _, result ->
            val place = result.getString(PickLocationFragment.KEY_PLACE).orEmpty()
            val lat = result.getDouble(PickLocationFragment.KEY_LAT, Double.NaN)
            val lng = result.getDouble(PickLocationFragment.KEY_LNG, Double.NaN)
            if (place.isNotBlank() && !lat.isNaN() && !lng.isNaN()) {
                pickedLat = lat
                pickedLng = lng
                _binding?.etPlace?.setText(place)
                _binding?.tilPlace?.error = null
            }
        }
        val openPicker = View.OnClickListener {
            findNavController().navigate(R.id.action_createKeo_to_pickLocation)
        }
        binding.etPlace.setOnClickListener(openPicker)
        binding.tilPlace.setEndIconOnClickListener(openPicker)

        // Mở bộ chọn ngày giờ cho cả Giờ hẹn và Hạn chốt
        binding.etTime.setOnClickListener {
            binding.tilTime.error = null
            showDateTimePicker(binding.etTime)
        }
        binding.etDeadline.setOnClickListener {
            binding.tilDeadline.error = null
            showDateTimePicker(binding.etDeadline)
        }

        // Logic xử lý khi bấm nút Tạo Kèo
        binding.btnCreate.setOnClickListener {
            if (!validateInputsOnUi()) return@setOnClickListener

            if (!requireContext().hasInternetConnection()) {
                Snackbar.make(requireView(), R.string.error_network, Snackbar.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val selectedSport = when (binding.chipGroupSport.checkedChipId) {
                R.id.chip_badminton -> Sport.BADMINTON
                R.id.chip_board_game -> Sport.BOARD_GAME
                else -> Sport.SOCCER
            }

            val title = binding.etName.text?.toString().orEmpty()
            val place = binding.etPlace.text?.toString().orEmpty()
            val capacity = binding.sliderPlayers.value.toInt()
            val minTrust = if (binding.switchTrust.isChecked) binding.sliderTrust.value.toInt() else null

            viewModel.createKeo(
                rawTitle = title,
                sport = selectedSport,
                rawPlaceName = place,
                lat = pickedLat,
                lng = pickedLng,
                startAtString = binding.etTime.text?.toString().orEmpty(),
                deadlineAtString = binding.etDeadline.text?.toString().orEmpty(),
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

    // Báo lỗi trực tiếp dưới từng ô nhập theo bảng mục 5
    private fun validateInputsOnUi(): Boolean {
        var isValid = true

        val title = binding.etName.text?.toString()?.trim().orEmpty()
        if (title.length !in 3..60) {
            binding.tilName.error = getString(R.string.error_keo_title_length)
            isValid = false
        }

        val place = binding.etPlace.text?.toString()?.trim().orEmpty()
        if (place.isBlank() || pickedLat == null || pickedLng == null) {
            binding.tilPlace.error = getString(R.string.error_keo_place_required)
            isValid = false
        }

        val deadlineStr = binding.etDeadline.text?.toString().orEmpty()
        val startStr = binding.etTime.text?.toString().orEmpty()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        val deadlineDate = if (deadlineStr.isBlank()) {
            binding.tilDeadline.error = getString(R.string.error_keo_deadline_required)
            isValid = false
            null
        } else {
            dateFormat.parse(deadlineStr)?.also { date ->
                if (date.time < System.currentTimeMillis() + 1 * 60 * 1000L) {
                    binding.tilDeadline.error = getString(R.string.error_keo_deadline_min)
                    isValid = false
                }
            }
        }

        if (startStr.isBlank()) {
            binding.tilTime.error = getString(R.string.error_keo_start_required)
            isValid = false
        } else {
            val startDate = dateFormat.parse(startStr)
            if (startDate != null && deadlineDate != null && !startDate.after(deadlineDate)) {
                binding.tilTime.error = getString(R.string.error_keo_start_after_deadline)
                isValid = false
            }
        }

        return isValid
    }

    private fun updatePlayers(value: Float) {
        binding.tvPlayersValue.text = getString(R.string.create_players_value, value.toInt())
    }

    private fun updateTrust(value: Float) {
        binding.tvTrustValue.text = getString(R.string.create_trust_value, value.toInt())
    }

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