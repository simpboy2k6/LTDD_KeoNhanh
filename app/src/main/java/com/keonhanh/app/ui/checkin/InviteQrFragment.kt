package com.keonhanh.app.ui.checkin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.keonhanh.app.R
import com.keonhanh.app.data.remote.KeoDoc
import com.keonhanh.app.data.remote.formatStartTime
import com.keonhanh.app.databinding.FragmentInviteQrBinding
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.appViewModels
import com.keonhanh.app.ui.common.collectWhenStarted
import com.keonhanh.app.ui.lobby.LobbyFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * S06 Mã mời vào kèo. Layout: res/layout/fragment_invite_qr.xml
 * Tuần 4 (Phong Hào): kèo thật từ Firestore; mã QR sinh bằng ZXing từ liên kết keonhanh://moi/{code}.
 * Máy khác quét mã này ở S07 (hoặc bằng camera của máy) là mở đúng phòng chờ.
 */
class InviteQrFragment : Fragment(R.layout.fragment_invite_qr) {

    private var _binding: FragmentInviteQrBinding? = null
    private val binding get() = _binding!!

    private val viewModel by appViewModels { container ->
        InviteQrViewModel(requireArguments().getString(LobbyFragment.ARG_KEO_ID).orEmpty(), container.keoRepository)
    }

    /** Mã đang hiện trên ảnh QR; chỉ vẽ lại khi mã đổi, không vẽ lại mỗi lần số người đổi. */
    private var qrCode: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentInviteQrBinding.bind(view)
        qrCode = null
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.ivQr.setImageDrawable(null) // bỏ ảnh QR mẫu của tuần 2
        binding.btnCopy.isEnabled = false
        binding.btnShare.isEnabled = false
        collectWhenStarted(viewModel.state, ::render)
    }

    private fun render(state: UiState<KeoDoc>) {
        when (state) {
            UiState.Loading -> Unit
            is UiState.Error -> {
                binding.tvSummary.text = state.message.resolve(requireContext())
                binding.btnCopy.isEnabled = false
                binding.btnShare.isEnabled = false
            }
            is UiState.Success -> show(state.data)
        }
    }

    private fun show(keo: KeoDoc) {
        val sport = keo.sportEnum
        binding.ivSport.setImageResource(sport.icon)
        binding.ivSport.backgroundTintList = ContextCompat.getColorStateList(requireContext(), sport.color)
        binding.tvTitle.text = keo.title
        binding.tvSummary.text = getString(R.string.invite_summary, keo.memberCount, keo.capacity, formatStartTime(keo.startAt))
        binding.tvInviteCode.text = keo.inviteCode
        binding.ivQr.contentDescription = getString(R.string.cd_invite_qr, keo.title)

        // Người vào bằng mã vẫn theo ngưỡng uy tín
        binding.tvTrustNote.isVisible = keo.minTrust != null
        keo.minTrust?.let { binding.tvTrustNote.text = getString(R.string.invite_trust_note, it) }

        val link = InviteLink.build(keo.inviteCode)
        drawQr(keo.inviteCode, link)

        binding.btnCopy.isEnabled = true
        binding.btnCopy.setOnClickListener {
            val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.invite_code_label), keo.inviteCode))
            Snackbar.make(it, R.string.invite_copied, Snackbar.LENGTH_SHORT).show()
        }
        binding.btnShare.isEnabled = true
        binding.btnShare.setOnClickListener {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                // Kèm cả mã 6 ký tự: ứng dụng nhắn tin không bấm được link keonhanh:// thì người nhận gõ mã ở S07
                putExtra(Intent.EXTRA_TEXT, getString(R.string.invite_share_text, keo.title, "$link (mã ${keo.inviteCode})"))
            }
            startActivity(Intent.createChooser(send, getString(R.string.invite_share)))
        }
    }

    private fun drawQr(code: String, link: String) {
        if (code == qrCode) return
        qrCode = code
        val size = resources.getDimensionPixelSize(R.dimen.qr_size)
        val dark = ContextCompat.getColor(requireContext(), R.color.kn_ink)
        val light = ContextCompat.getColor(requireContext(), android.R.color.white)
        viewLifecycleOwner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) { qrBitmap(link, size, dark, light) }
            _binding?.ivQr?.setImageBitmap(bitmap)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
