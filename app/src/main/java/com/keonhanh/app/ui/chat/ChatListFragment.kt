package com.keonhanh.app.ui.chat

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.keonhanh.app.R
import com.keonhanh.app.data.model.ChatRoom
import com.keonhanh.app.databinding.FragmentChatListBinding
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.appViewModels
import com.keonhanh.app.ui.common.collectWhenStarted
import com.keonhanh.app.ui.lobby.LobbyFragment

/**
 * S18 Danh sách chat (Phong Hào). Layout: res/layout/fragment_chat_list.xml
 * Tuần 4: dữ liệu thật từ ChatListViewModel; có 3 trạng thái đang tải, rỗng, lỗi.
 */
class ChatListFragment : Fragment(R.layout.fragment_chat_list) {

    private var _binding: FragmentChatListBinding? = null
    private val binding get() = _binding!!

    private val viewModel by appViewModels { container ->
        ChatListViewModel(
            container.memberRepository,
            container.keoRepository,
            container.chatRepository,
            container.authRepository,
        )
    }

    private val adapter = ChatRoomAdapter { room ->
        findNavController().navigate(
            R.id.action_chatList_to_lobbyChat,
            bundleOf(LobbyFragment.ARG_KEO_ID to room.keo.id),
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentChatListBinding.bind(view)
        binding.rvRooms.adapter = adapter
        collectWhenStarted(viewModel.state, ::render)
    }

    private fun render(state: UiState<List<ChatRoom>>) {
        binding.progressLoading.isVisible = state is UiState.Loading
        when (state) {
            UiState.Loading -> binding.empty.root.isVisible = false
            is UiState.Error -> {
                adapter.submitList(emptyList())
                binding.empty.root.isVisible = true
                binding.empty.ivEmptyIcon.setImageResource(R.drawable.ic_error_24)
                binding.empty.tvEmptyTitle.setText(R.string.chat_list_error_title)
                binding.empty.tvEmptyMessage.text = state.message.resolve(requireContext())
            }
            is UiState.Success -> {
                adapter.submitList(state.data)
                binding.empty.root.isVisible = state.data.isEmpty()
                binding.empty.ivEmptyIcon.setImageResource(R.drawable.ic_chat_bubble_24)
                binding.empty.tvEmptyTitle.setText(R.string.chat_list_empty_title)
                binding.empty.tvEmptyMessage.setText(R.string.chat_list_empty_message)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvRooms.adapter = null
        _binding = null
    }
}
