package com.keonhanh.app.ui.mykeo

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.tabs.TabLayout
import com.keonhanh.app.R
import com.keonhanh.app.databinding.FragmentMyKeoBinding
import com.keonhanh.app.ui.common.KeoAdapter
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.appViewModels
import com.keonhanh.app.ui.common.collectWhenStarted
import com.keonhanh.app.ui.lobby.LobbyFragment

/** S17 Kèo của tôi (TV3). Layout: res/layout/fragment_my_keo.xml */
class MyKeoFragment : Fragment(R.layout.fragment_my_keo) {

    private var _binding: FragmentMyKeoBinding? = null
    private val binding get() = _binding!!

    private val viewModel by appViewModels { container ->
        MyKeoViewModel(container.memberRepository, container.keoRepository, container.authRepository)
    }

    private lateinit var adapter: KeoAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentMyKeoBinding.bind(view)

        adapter = KeoAdapter { keo ->
            findNavController().navigate(
                R.id.action_myKeo_to_lobby,
                bundleOf(LobbyFragment.ARG_KEO_ID to keo.id),
            )
        }
        binding.rvKeo.adapter = adapter

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = render(viewModel.state.value)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        collectWhenStarted(viewModel.state) { render(it) }
    }

    /** Tab 0: đang tham gia · tab 1: đang làm chủ · tab 2: đã xong */
    private fun render(state: UiState<MyKeoUi>) {
        binding.progressLoading.isVisible = state is UiState.Loading
        when (state) {
            UiState.Loading -> {
                adapter.submitList(emptyList())
                binding.empty.root.isVisible = false
            }
            is UiState.Error -> {
                adapter.submitList(emptyList())
                binding.empty.root.isVisible = true
                binding.empty.ivEmptyIcon.setImageResource(R.drawable.ic_error_24)
                binding.empty.tvEmptyTitle.setText(R.string.my_keo_error_title)
                binding.empty.tvEmptyMessage.text = state.message.resolve(requireContext())
            }
            is UiState.Success -> {
                val tab = binding.tabLayout.selectedTabPosition.coerceAtLeast(0)
                val items = when (tab) {
                    0 -> state.data.joined
                    1 -> state.data.hosting
                    else -> state.data.done
                }
                adapter.submitList(items)
                binding.empty.root.isVisible = items.isEmpty()
                binding.empty.ivEmptyIcon.setImageResource(R.drawable.ic_event_note_24)
                binding.empty.tvEmptyTitle.setText(R.string.my_keo_empty_title)
                binding.empty.tvEmptyMessage.setText(
                    when (tab) {
                        0 -> R.string.my_keo_empty_joined
                        1 -> R.string.my_keo_empty_hosting
                        else -> R.string.my_keo_empty_message
                    },
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
