package com.keonhanh.app.ui.lobby

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.keonhanh.app.R
import com.keonhanh.app.data.model.Message
import com.keonhanh.app.data.sample.SampleData
import com.keonhanh.app.databinding.FragmentLobbyChatBinding
import com.keonhanh.app.util.padForKeyboard

/** S16 Chat phòng chờ (TV3). Layout: fragment_lobby_chat.xml + item_message_me / item_message_other */
class LobbyChatFragment : Fragment(R.layout.fragment_lobby_chat) {

    private var _binding: FragmentLobbyChatBinding? = null
    private val binding get() = _binding!!

    private val adapter = MessageAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLobbyChatBinding.bind(view)
        view.padForKeyboard()

        val keo = SampleData.findKeo(requireArguments().getString(LobbyFragment.ARG_KEO_ID).orEmpty())
        binding.toolbar.title = keo.title
        binding.toolbar.subtitle = getString(R.string.chat_members_subtitle, keo.members.size)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.rvMessages.adapter = adapter
        adapter.submitList(SampleData.messagesFor(keo.id))

        // Tuần 2: chỉ thêm vào danh sách trên máy. Tuần 3: ghi vào Firestore, nghe realtime.
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) return@setOnClickListener
            val newMessage = Message(
                id = "local-${adapter.itemCount}",
                sender = SampleData.me,
                text = text,
                time = "",
                isMine = true,
            )
            adapter.submitList(adapter.currentList + newMessage) {
                _binding?.rvMessages?.scrollToPosition(adapter.itemCount - 1)
            }
            binding.etMessage.text?.clear()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
