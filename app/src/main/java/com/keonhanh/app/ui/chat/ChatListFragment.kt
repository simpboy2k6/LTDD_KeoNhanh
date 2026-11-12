package com.keonhanh.app.ui.chat

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.keonhanh.app.R
import com.keonhanh.app.data.sample.SampleData
import com.keonhanh.app.databinding.FragmentChatListBinding
import com.keonhanh.app.ui.lobby.LobbyFragment

/** S18 Danh sách chat (TV3, P1). Layout: res/layout/fragment_chat_list.xml */
class ChatListFragment : Fragment(R.layout.fragment_chat_list) {

    private var _binding: FragmentChatListBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentChatListBinding.bind(view)

        val adapter = ChatRoomAdapter { room ->
            findNavController().navigate(
                R.id.action_chatList_to_lobbyChat,
                bundleOf(LobbyFragment.ARG_KEO_ID to room.keo.id),
            )
        }
        binding.rvRooms.adapter = adapter
        adapter.submitList(SampleData.chatRooms)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
