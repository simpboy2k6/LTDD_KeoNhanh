package com.keonhanh.app.ui.lobby

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.keonhanh.app.R
import com.keonhanh.app.data.model.Message
import com.keonhanh.app.databinding.FragmentLobbyChatBinding
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.appViewModels
import com.keonhanh.app.ui.common.collectWhenStarted
import com.keonhanh.app.util.padForKeyboard

/** S16 Chat phòng chờ (TV3). Layout: fragment_lobby_chat.xml + item_message_me / item_message_other */
class LobbyChatFragment : Fragment(R.layout.fragment_lobby_chat) {

    private var _binding: FragmentLobbyChatBinding? = null
    private val binding get() = _binding!!

    private val keoId: String by lazy { requireArguments().getString(LobbyFragment.ARG_KEO_ID).orEmpty() }

    private val viewModel by appViewModels { container ->
        ChatViewModel(
            keoId = keoId,
            keoRepository = container.keoRepository,
            chatRepository = container.chatRepository,
            authRepository = container.authRepository,
            userRepository = container.userRepository,
        )
    }

    private val adapter = MessageAdapter(onLongClickMine = { message -> confirmDelete(message) })

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLobbyChatBinding.bind(view)
        view.padForKeyboard()

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.rvMessages.adapter = adapter

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) return@setOnClickListener
            viewModel.send(text)
            // Xóa ô nhập ngay; tin hiện ra khi Firestore báo về (thường dưới 1 giây)
            binding.etMessage.text?.clear()
        }

        collectWhenStarted(viewModel.state) { state ->
            when (state) {
                UiState.Loading -> Unit
                is UiState.Success -> showChat(state.data)
                is UiState.Error ->
                    Snackbar.make(binding.root, state.message.resolve(requireContext()), Snackbar.LENGTH_LONG).show()
            }
        }
        collectWhenStarted(viewModel.events) { event ->
            when (event) {
                is ChatEvent.ShowMessage ->
                    Snackbar.make(binding.root, event.text.resolve(requireContext()), Snackbar.LENGTH_SHORT).show()
                is ChatEvent.RestoreDraft ->
                    if (binding.etMessage.text.isNullOrEmpty()) binding.etMessage.setText(event.text)
            }
        }
    }

    private fun showChat(chat: ChatUi) {
        binding.toolbar.title = chat.title
        binding.toolbar.subtitle = getString(R.string.chat_members_subtitle, chat.memberCount)
        val hadNewMessage = chat.messages.lastOrNull()?.id != adapter.currentList.lastOrNull()?.id
        adapter.submitList(chat.messages) {
            // Có tin mới thì cuộn xuống cuối; xóa tin hay cập nhật giờ thì giữ nguyên vị trí đang đọc
            if (hadNewMessage) _binding?.rvMessages?.scrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun confirmDelete(message: Message) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.chat_delete_title)
            .setMessage(message.text)
            .setPositiveButton(R.string.chat_delete_confirm) { _, _ -> viewModel.delete(message.id) }
            .setNegativeButton(R.string.dialog_dismiss, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
