package com.keonhanh.app.ui.lobby

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.keonhanh.app.R
import com.keonhanh.app.data.model.CheckInState
import com.keonhanh.app.data.model.KeoStatus
import com.keonhanh.app.data.model.Member
import com.keonhanh.app.data.model.MyRole
import com.keonhanh.app.data.sample.SampleData
import com.keonhanh.app.databinding.FragmentLobbyBinding
import com.keonhanh.app.ui.checkin.render
import com.keonhanh.app.ui.common.MemberAdapter
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.appViewModels
import com.keonhanh.app.ui.common.collectWhenStarted
import com.keonhanh.app.util.bindAvatar
import com.keonhanh.app.util.showComingSoon

/**
 * S15 Chi tiết kèo / phòng chờ (TV3). Layout: res/layout/fragment_lobby.xml
 *
 * Fragment chỉ làm 2 việc: vẽ LobbyUi mà ViewModel đưa ra, và báo cho ViewModel khi người dùng bấm.
 * Không gọi Firestore ở đây; mọi logic (chọn nút, tham gia, rời) nằm trong LobbyViewModel.
 */
class LobbyFragment : Fragment(R.layout.fragment_lobby) {

    private var _binding: FragmentLobbyBinding? = null
    private val binding get() = _binding!!

    private val keoId: String by lazy { requireArguments().getString(ARG_KEO_ID).orEmpty() }

    // ViewModel lấy Repository từ AppContainer (xem ui/common/FragmentExt.kt)
    private val viewModel by appViewModels { container ->
        LobbyViewModel(
            keoId = keoId,
            keoRepository = container.keoRepository,
            memberRepository = container.memberRepository,
            chatRepository = container.chatRepository,
            authRepository = container.authRepository,
            userRepository = container.userRepository,
        )
    }

    private var memberAdapter: MemberAdapter? = null
    private var snackbar: Snackbar? = null
    private var lastUi: LobbyUi? = null
    private var primaryEnabled = false

    /** Thẻ check-in (Minh Khoa): tuần 3 vẫn duyệt 7 trạng thái bằng menu demo, tuần 4 tính từ GPS. */
    private var demoStates: List<CheckInState> = emptyList()
    private var demoIndex = 0
    private var checkInState: CheckInState? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLobbyBinding.bind(view)
        val nav = findNavController()
        val args = bundleOf(ARG_KEO_ID to keoId)

        binding.toolbar.setNavigationOnClickListener { nav.navigateUp() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_invite -> nav.navigate(R.id.action_lobby_to_inviteQr, args)
                R.id.action_requests -> lastUi?.let { showRequests(it.pendingRequests) }
                R.id.action_demo_checkin -> if (demoStates.isNotEmpty()) {
                    demoIndex = (demoIndex + 1) % demoStates.size
                    showCheckIn(demoStates[demoIndex])
                }
            }
            true
        }

        val openChat = View.OnClickListener {
            if (lastUi?.canOpenChat == true) {
                nav.navigate(R.id.action_lobby_to_lobbyChat, args)
            } else {
                showMessage(getString(R.string.lobby_chat_locked))
            }
        }
        binding.cardChatPreview.setOnClickListener(openChat)
        binding.btnChat.setOnClickListener(openChat)
        binding.btnPrimary.setOnClickListener { onPrimaryClick(it) }
        binding.checkin.btnCheckinSecondary.setOnClickListener { it.showComingSoon() }

        collectWhenStarted(viewModel.state) { render(it) }
        collectWhenStarted(viewModel.busy) { busy ->
            // Thẻ check-in tự quản lý nút chính; chỉ khóa nút khi đang hiện nút tham gia/rời
            if (checkInState == null) binding.btnPrimary.isEnabled = primaryEnabled && !busy
        }
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                is LobbyEvent.ShowMessage -> showMessage(event.text.resolve(requireContext()))
                LobbyEvent.Closed -> nav.navigateUp()
            }
        }
    }

    /** Thông báo ngắn, đặt ngay trên thanh nút để không che nút chính; tự tắt khi rời màn (không đè sang S16). */
    private fun showMessage(text: String) {
        snackbar = Snackbar.make(binding.root, text, Snackbar.LENGTH_SHORT).apply {
            if (binding.barActions.isVisible) anchorView = binding.barActions
            show()
        }
    }

    private fun render(state: UiState<LobbyUi>) {
        binding.progressLoading.isVisible = state is UiState.Loading
        binding.scrollContent.isVisible = state is UiState.Success
        binding.barActions.isVisible = state is UiState.Success
        binding.errorState.root.isVisible = state is UiState.Error

        when (state) {
            UiState.Loading -> Unit
            is UiState.Error -> {
                lastUi = null
                binding.toolbar.menu.setGroupVisible(0, false)
                binding.errorState.ivEmptyIcon.setImageResource(R.drawable.ic_error_24)
                binding.errorState.tvEmptyTitle.setText(R.string.lobby_error_title)
                binding.errorState.tvEmptyMessage.text = state.message.resolve(requireContext())
            }
            is UiState.Success -> showKeo(state.data)
        }
    }

    private fun showKeo(ui: LobbyUi) {
        lastUi = ui
        val keo = ui.keo
        val ctx = requireContext()
        val isCheckIn = keo.status == KeoStatus.CHECK_IN
        val showCheckInCard = isCheckIn && keo.myRole != MyRole.NONE

        binding.ivSport.setImageResource(keo.sport.icon)
        binding.ivSport.backgroundTintList = ContextCompat.getColorStateList(ctx, keo.sport.color)
        binding.tvTitle.text = keo.title
        binding.tvSportTag.setText(keo.sport.label)
        binding.tvSportTag.backgroundTintList = ContextCompat.getColorStateList(ctx, keo.sport.tint)
        binding.tvSportTag.setTextColor(ContextCompat.getColor(ctx, keo.sport.textOnTint))

        // Khoảng cách tới điểm hẹn cần GPS (tuần 4) nên ở đây chỉ hiện tên điểm hẹn
        binding.tvPlace.text = keo.place
        binding.tvTime.text = ui.hostTrust
            ?.let { getString(R.string.lobby_host_line, keo.time, keo.hostName, it) }
            ?: getString(R.string.lobby_host_line_no_trust, keo.time, keo.hostName)

        // Đếm ngược chỉ có ý nghĩa trước hạn chốt; sau đó thẻ check-in (nếu là thành viên) thế chỗ
        binding.checkin.root.isVisible = showCheckInCard
        binding.cardCountdown.isVisible = keo.status == KeoStatus.OPEN || keo.status == KeoStatus.FULL
        binding.tvCountdown.text = keo.deadlineCountdown
        binding.progressDeadline.progress = ui.deadlineProgress

        binding.tvMembersCount.text = if (isCheckIn) {
            getString(R.string.lobby_checked_in_count, keo.members.count { it.checkedIn }, keo.members.size)
        } else {
            getString(R.string.lobby_members_count, keo.members.size, keo.maxSlots)
        }
        binding.tvMinTrust.isVisible = keo.minTrust != null && !isCheckIn
        keo.minTrust?.let { binding.tvMinTrust.text = getString(R.string.lobby_min_trust, it) }
        // Màn này vẽ lại mỗi giây (đồng hồ): chỉ tạo Adapter mới khi đổi chế độ, còn lại để DiffUtil cập nhật
        if (memberAdapter?.checkInMode != isCheckIn) {
            memberAdapter = MemberAdapter(checkInMode = isCheckIn).also { binding.rvMembers.adapter = it }
        }
        memberAdapter?.submitKeo(keo)

        showChatPreview(ui)
        showMenu(ui)

        if (showCheckInCard) {
            if (demoStates.isEmpty()) {
                demoStates = SampleData.checkInDemoStates(keo)
                demoIndex = demoStates.indexOfFirst { it is CheckInState.InRange }.coerceAtLeast(0)
            }
            showCheckIn(checkInState ?: demoStates[demoIndex])
        } else {
            checkInState = null
            binding.btnPrimary.setText(ui.primaryAction.label)
            primaryEnabled = ui.primaryAction.enabled
            binding.btnPrimary.isEnabled = primaryEnabled && !viewModel.busy.value
        }
    }

    private fun showChatPreview(ui: LobbyUi) {
        val latest = ui.latestMessage
        binding.tvPreviewAvatar.isVisible = latest != null
        if (latest != null) {
            binding.tvPreviewAvatar.bindAvatar(latest.sender.name, latest.sender.id)
            binding.tvPreviewMessage.text = latest.text
        } else {
            binding.tvPreviewMessage.setText(if (ui.canOpenChat) R.string.lobby_chat_empty else R.string.lobby_chat_locked)
        }
    }

    private fun showMenu(ui: LobbyUi) {
        val keo = ui.keo
        val menu = binding.toolbar.menu
        // Mời bằng QR: kèo đang mở và mình đã ở trong kèo (thành viên nào cũng mời được)
        menu.findItem(R.id.action_invite).isVisible = keo.status == KeoStatus.OPEN && keo.myRole != MyRole.NONE
        menu.findItem(R.id.action_requests).apply {
            isVisible = ui.pendingRequests.isNotEmpty()
            title = getString(R.string.menu_requests, ui.pendingRequests.size)
        }
        menu.findItem(R.id.action_demo_checkin).isVisible = keo.status == KeoStatus.CHECK_IN && keo.myRole != MyRole.NONE
    }

    private fun onPrimaryClick(button: View) {
        val state = checkInState
        if (state != null) {
            onCheckInClick(button, state)
            return
        }
        when (lastUi?.primaryAction) {
            PrimaryAction.LEAVE -> confirm(R.string.dialog_leave_title, R.string.dialog_leave_message, R.string.lobby_leave)
            PrimaryAction.CANCEL_KEO -> confirm(R.string.dialog_cancel_title, R.string.dialog_cancel_message, R.string.lobby_cancel)
            else -> viewModel.onPrimaryAction()
        }
    }

    /** Hộp thoại hỏi lại trước khi rời hoặc hủy kèo (S19, bản đơn giản). */
    private fun confirm(@StringRes title: Int, @StringRes message: Int, @StringRes positive: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positive) { _, _ -> viewModel.onPrimaryAction() }
            .setNegativeButton(R.string.dialog_dismiss, null)
            .show()
    }

    /** Chủ kèo duyệt người xin vào: tạm dùng hộp thoại thay cho màn S08 (P1). */
    private fun showRequests(requests: List<Member>) {
        if (requests.isEmpty()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_requests_title)
            .setItems(requests.map { it.name }.toTypedArray()) { _, which -> showRequest(requests[which]) }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    private fun showRequest(member: Member) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_request_title, member.name))
            .setPositiveButton(R.string.action_approve) { _, _ -> viewModel.approve(member.id) }
            .setNegativeButton(R.string.action_reject) { _, _ -> viewModel.reject(member.id) }
            .setNeutralButton(R.string.dialog_dismiss, null)
            .show()
    }

    private fun showCheckIn(state: CheckInState) {
        checkInState = state
        binding.checkin.render(state, lastUi?.keo?.checkInWindow.orEmpty(), binding.btnPrimary)
    }

    private fun onCheckInClick(button: View, state: CheckInState) {
        when (state) {
            is CheckInState.InRange -> {
                // Tuần 4 (Minh Khoa): ghi checkInAt, checkInDistanceM vào members/{uid} trên Firestore
                showCheckIn(CheckInState.Done(time = "16:52", distanceM = state.distanceM))
                showMessage(getString(R.string.checkin_success))
            }
            // Tuần 4: xin quyền ACCESS_FINE_LOCATION
            else -> button.showComingSoon()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        snackbar?.dismiss()
        snackbar = null
        memberAdapter = null
        _binding = null
    }

    companion object {
        const val ARG_KEO_ID = "keoId"
    }
}
