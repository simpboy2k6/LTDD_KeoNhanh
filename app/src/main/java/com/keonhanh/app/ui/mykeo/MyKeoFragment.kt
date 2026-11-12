package com.keonhanh.app.ui.mykeo

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.tabs.TabLayout
import com.keonhanh.app.R
import com.keonhanh.app.data.model.MyRole
import com.keonhanh.app.data.sample.SampleData
import com.keonhanh.app.databinding.FragmentMyKeoBinding
import com.keonhanh.app.ui.common.KeoAdapter
import com.keonhanh.app.ui.lobby.LobbyFragment

/** S17 Kèo của tôi (TV3). Layout: res/layout/fragment_my_keo.xml */
class MyKeoFragment : Fragment(R.layout.fragment_my_keo) {

    private var _binding: FragmentMyKeoBinding? = null
    private val binding get() = _binding!!

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

        binding.empty.tvEmptyTitle.setText(R.string.my_keo_empty_title)
        binding.empty.tvEmptyMessage.setText(R.string.my_keo_empty_message)

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = showTab(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        showTab(binding.tabLayout.selectedTabPosition.coerceAtLeast(0))
    }

    /** Tab 0: đang tham gia · tab 1: đang làm chủ · tab 2: đã xong (chưa có dữ liệu mẫu → trạng thái rỗng) */
    private fun showTab(position: Int) {
        val items = when (position) {
            0 -> SampleData.keos.filter { it.myRole == MyRole.MEMBER }
            1 -> SampleData.keos.filter { it.myRole == MyRole.HOST }
            else -> emptyList()
        }
        adapter.submitList(items)
        binding.empty.root.isVisible = items.isEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
