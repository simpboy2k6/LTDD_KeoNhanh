package com.keonhanh.app.ui.checkin

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.keonhanh.app.R
import com.keonhanh.app.data.model.Keo
import com.keonhanh.app.data.sample.SampleData
import com.keonhanh.app.databinding.FragmentScanQrBinding
import com.keonhanh.app.ui.lobby.LobbyFragment
import com.keonhanh.app.util.padForKeyboard

/** S07 Quét mã mời (TV1). Layout: res/layout/fragment_scan_qr.xml */
class ScanQrFragment : Fragment(R.layout.fragment_scan_qr) {

    private var _binding: FragmentScanQrBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentScanQrBinding.bind(view)
        view.padForKeyboard()

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        // Tuần 2: chạm vào khung = giả lập quét được mã của kèo "Catan cuối tuần".
        // Tuần 4: Code Scanner đọc chuỗi keonhanh://moi/{code}, lấy {code} rồi gọi openByCode.
        binding.frameScan.setOnClickListener { openKeo(SampleData.findKeo("k4")) }

        binding.btnOpen.setOnClickListener { openByCode() }
        binding.etCode.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                openByCode()
                true
            } else {
                false
            }
        }
        binding.etCode.doAfterTextChanged { binding.tilCode.error = null }
    }

    private fun openByCode() {
        val keo = SampleData.findByInviteCode(binding.etCode.text?.toString().orEmpty())
        if (keo == null) {
            binding.tilCode.error = getString(R.string.scan_code_not_found)
        } else {
            openKeo(keo)
        }
    }

    /** Mở S15 của kèo; nút ở S15 vẫn theo luật thường ("Tham gia kèo" hoặc "Xin vào"). */
    private fun openKeo(keo: Keo) {
        findNavController().navigate(
            R.id.action_scanQr_to_lobby,
            bundleOf(LobbyFragment.ARG_KEO_ID to keo.id),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
