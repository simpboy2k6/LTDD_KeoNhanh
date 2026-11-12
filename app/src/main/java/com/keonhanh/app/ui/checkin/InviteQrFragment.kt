package com.keonhanh.app.ui.checkin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.keonhanh.app.R
import com.keonhanh.app.data.sample.SampleData
import com.keonhanh.app.databinding.FragmentInviteQrBinding
import com.keonhanh.app.ui.lobby.LobbyFragment

/** S06 Mã mời vào kèo (TV1). Layout: res/layout/fragment_invite_qr.xml */
class InviteQrFragment : Fragment(R.layout.fragment_invite_qr) {

    private var _binding: FragmentInviteQrBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentInviteQrBinding.bind(view)
        val keo = SampleData.findKeo(requireArguments().getString(LobbyFragment.ARG_KEO_ID).orEmpty())

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.ivSport.setImageResource(keo.sport.icon)
        binding.ivSport.backgroundTintList = ContextCompat.getColorStateList(requireContext(), keo.sport.color)
        binding.tvTitle.text = keo.title
        binding.tvSummary.text = getString(R.string.invite_summary, keo.members.size, keo.maxSlots, keo.time)

        // Tuần 2: ảnh QR mẫu. Tuần 4: sinh QR từ link mời bằng ZXing (BarcodeEncoder)
        binding.ivQr.contentDescription = getString(R.string.cd_invite_qr, keo.title)
        binding.tvInviteCode.text = keo.inviteCode

        // Người vào bằng mã vẫn theo ngưỡng uy tín (nhóm tạm chốt ở mục 9)
        binding.tvTrustNote.isVisible = keo.minTrust != null
        keo.minTrust?.let { binding.tvTrustNote.text = getString(R.string.invite_trust_note, it) }

        binding.btnCopy.setOnClickListener {
            val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.invite_code_label), keo.inviteCode))
            Snackbar.make(it, R.string.invite_copied, Snackbar.LENGTH_SHORT).show()
        }

        binding.btnShare.setOnClickListener {
            // Tuần 4: khai báo deep link keonhanh://moi/{code} trong nav_graph để link mở thẳng S15
            val link = getString(R.string.invite_link, keo.inviteCode)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, getString(R.string.invite_share_text, keo.title, link))
            }
            startActivity(Intent.createChooser(send, getString(R.string.invite_share)))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
