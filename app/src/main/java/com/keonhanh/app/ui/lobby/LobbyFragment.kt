package com.keonhanh.app.ui.lobby

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.keonhanh.app.R
import com.keonhanh.app.data.model.CheckInState
import com.keonhanh.app.data.model.Keo
import com.keonhanh.app.data.model.KeoStatus
import com.keonhanh.app.data.model.MyRole
import com.keonhanh.app.data.sample.SampleData
import com.keonhanh.app.databinding.FragmentLobbyBinding
import com.keonhanh.app.ui.checkin.render
import com.keonhanh.app.ui.common.MemberAdapter
import com.keonhanh.app.util.bindAvatar
import com.keonhanh.app.util.formatKm
import com.keonhanh.app.util.showComingSoon

/** S15 Chi tiết kèo / phòng chờ (TV3). Layout: res/layout/fragment_lobby.xml */
class LobbyFragment : Fragment(R.layout.fragment_lobby) {

    private var _binding: FragmentLobbyBinding? = null
    private val binding get() = _binding!!

    private lateinit var keo: Keo

    /** Tuần 2: duyệt lần lượt 7 trạng thái thẻ check-in bằng menu demo. Tuần 4: tính từ GPS. */
    private var demoStates: List<CheckInState> = emptyList()
    private var demoIndex = 0
    private var checkInState: CheckInState? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLobbyBinding.bind(view)
        val nav = findNavController()
        keo = SampleData.findKeo(requireArguments().getString(ARG_KEO_ID).orEmpty())
        val args = bundleOf(ARG_KEO_ID to keo.id)

        showKeoInfo()
        setupToolbar(args)

        binding.cardChatPreview.setOnClickListener { nav.navigate(R.id.action_lobby_to_lobbyChat, args) }
        binding.btnChat.setOnClickListener { nav.navigate(R.id.action_lobby_to_lobbyChat, args) }

        if (keo.status == KeoStatus.CHECK_IN) {
            // Mặc định trạng thái "đủ gần" để demo nút Check-in
            demoStates = SampleData.checkInDemoStates(keo)
            demoIndex = demoStates.indexOfFirst { it is CheckInState.InRange }.coerceAtLeast(0)
            showCheckIn(demoStates[demoIndex])
        } else {
            binding.btnPrimary.setText(primaryTextForOpenKeo())
        }
        binding.btnPrimary.setOnClickListener { onPrimaryClick(it) }
        binding.checkin.btnCheckinSecondary.setOnClickListener { it.showComingSoon() }
    }

    private fun setupToolbar(args: Bundle) {
        val nav = findNavController()
        binding.toolbar.setNavigationOnClickListener { nav.navigateUp() }

        // Mời bằng QR: kèo đang mở và mình đã ở trong kèo (thành viên nào cũng mời được)
        binding.toolbar.menu.findItem(R.id.action_invite).isVisible =
            keo.status == KeoStatus.OPEN && keo.myRole != MyRole.NONE
        binding.toolbar.menu.findItem(R.id.action_demo_checkin).isVisible =
            keo.status == KeoStatus.CHECK_IN

        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_invite -> nav.navigate(R.id.action_lobby_to_inviteQr, args)
                R.id.action_demo_checkin -> {
                    demoIndex = (demoIndex + 1) % demoStates.size
                    showCheckIn(demoStates[demoIndex])
                }
            }
            true
        }
    }

    private fun showKeoInfo() {
        val ctx = requireContext()
        val isCheckIn = keo.status == KeoStatus.CHECK_IN

        binding.ivSport.setImageResource(keo.sport.icon)
        binding.ivSport.backgroundTintList = ContextCompat.getColorStateList(ctx, keo.sport.color)
        binding.tvTitle.text = keo.title
        binding.tvSportTag.setText(keo.sport.label)
        binding.tvSportTag.backgroundTintList = ContextCompat.getColorStateList(ctx, keo.sport.tint)
        binding.tvSportTag.setTextColor(ContextCompat.getColor(ctx, keo.sport.textOnTint))

        binding.tvPlace.text = getString(R.string.keo_place_distance, keo.place, formatKm(keo.distanceKm))
        binding.tvTime.text = getString(R.string.lobby_host_line, keo.time, keo.hostName, keo.hostTrust)

        // Thẻ check-in thay chỗ thẻ đếm ngược khi kèo sang trạng thái Check-in
        binding.checkin.root.isVisible = isCheckIn
        binding.cardCountdown.isVisible = !isCheckIn && keo.status != KeoStatus.ENDED
        binding.tvCountdown.text = keo.deadlineCountdown
        binding.progressDeadline.progress = 55 // Tuần 3: CountDownTimer tới hạn chốt

        binding.tvMembersCount.text = if (isCheckIn) {
            getString(R.string.lobby_checked_in_count, keo.members.count { it.checkedIn }, keo.members.size)
        } else {
            getString(R.string.lobby_members_count, keo.members.size, keo.maxSlots)
        }
        binding.tvMinTrust.isVisible = keo.minTrust != null && !isCheckIn
        keo.minTrust?.let { binding.tvMinTrust.text = getString(R.string.lobby_min_trust, it) }
        binding.rvMembers.adapter = MemberAdapter(checkInMode = isCheckIn).apply { submitKeo(keo) }

        val firstMessage = SampleData.messagesFor(keo.id).first()
        binding.tvPreviewAvatar.bindAvatar(firstMessage.sender.name, firstMessage.sender.id)
        binding.tvPreviewMessage.text = firstMessage.text
    }

    /** Hàng "Đang mở" của bảng nút (mục 3). Người vào bằng mã mời cũng theo luật này. */
    private fun primaryTextForOpenKeo(): Int = when {
        keo.myRole == MyRole.HOST -> R.string.lobby_cancel
        keo.myRole == MyRole.MEMBER -> R.string.lobby_leave
        keo.minTrust != null -> R.string.lobby_request_join
        else -> R.string.lobby_join
    }

    private fun showCheckIn(state: CheckInState) {
        checkInState = state
        binding.checkin.render(state, keo.checkInWindow, binding.btnPrimary)
    }

    private fun onPrimaryClick(button: View) {
        when (val state = checkInState) {
            is CheckInState.InRange -> {
                // Tuần 4: ghi bản ghi check-in (giờ, khoảng cách, sai số GPS) vào Firestore
                showCheckIn(CheckInState.Done(time = "16:52", distanceM = state.distanceM))
                Snackbar.make(button, R.string.checkin_success, Snackbar.LENGTH_SHORT).show()
            }
            // Tuần 4: xin quyền ACCESS_FINE_LOCATION; tuần 3: tham gia / rời kèo bằng transaction
            else -> button.showComingSoon()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_KEO_ID = "keoId"
    }
}
