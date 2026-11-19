package com.keonhanh.app.ui.checkin

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.keonhanh.app.R
import com.keonhanh.app.databinding.FragmentScanQrBinding
import com.keonhanh.app.ui.common.appViewModels
import com.keonhanh.app.ui.common.collectWhenStarted
import com.keonhanh.app.ui.lobby.LobbyFragment
import com.keonhanh.app.util.padForKeyboard

/**
 * S07 Quét mã mời. Layout: res/layout/fragment_scan_qr.xml
 * Tuần 4 (Phong Hào):
 *  - Quét bằng Google code scanner: màn quét do Google Play services mở, app không cần xin quyền camera.
 *  - Đọc được keonhanh://moi/{code} (hoặc mã trơn) thì tìm kèo bằng findByInviteCode rồi mở S15.
 *  - Ô nhập mã tay để dự phòng khi máy không mở được trình quét.
 */
class ScanQrFragment : Fragment(R.layout.fragment_scan_qr) {

    private var _binding: FragmentScanQrBinding? = null
    private val binding get() = _binding!!

    private val viewModel by appViewModels { container -> ScanQrViewModel(container.keoRepository) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentScanQrBinding.bind(view)
        view.padForKeyboard()

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.frameScan.setOnClickListener { startScan() }

        binding.btnOpen.setOnClickListener { openTyped() }
        binding.etCode.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                openTyped()
                true
            } else {
                false
            }
        }
        binding.etCode.doAfterTextChanged { binding.tilCode.error = null }

        collectWhenStarted(viewModel.searching) { searching -> binding.btnOpen.isEnabled = !searching }
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                is ScanEvent.Open -> findNavController().navigate(
                    R.id.action_scanQr_to_lobby,
                    bundleOf(LobbyFragment.ARG_KEO_ID to event.keoId),
                )
                is ScanEvent.Error -> {
                    val message = event.message.resolve(requireContext())
                    if (event.fromCamera) {
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                    } else {
                        binding.tilCode.error = message
                    }
                }
            }
        }

        // Vào màn là mở camera luôn, đỡ một lần chạm; quay lại từ màn quét thì không tự mở nữa
        if (savedInstanceState == null) startScan()
    }

    private fun openTyped() {
        viewModel.open(binding.etCode.text?.toString().orEmpty(), fromCamera = false)
    }

    private fun startScan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(requireContext(), options)
            .startScan()
            .addOnSuccessListener { barcode -> viewModel.open(barcode.rawValue.orEmpty(), fromCamera = true) }
            .addOnFailureListener {
                // Máy thiếu Google Play services hoặc chưa tải xong module quét: còn ô nhập mã tay
                _binding?.let { b -> Snackbar.make(b.root, R.string.scan_camera_unavailable, Snackbar.LENGTH_LONG).show() }
            }
        // Người dùng bấm Back ở màn quét: không làm gì, vẫn ở S07 để nhập mã tay
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
