package com.keonhanh.app.ui.explore

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.keonhanh.app.R
import com.keonhanh.app.data.model.Sport
import com.keonhanh.app.data.sample.SampleData
import com.keonhanh.app.databinding.FragmentExploreListBinding
import com.keonhanh.app.ui.common.KeoAdapter
import com.keonhanh.app.ui.lobby.LobbyFragment

/** S10 Khám phá – danh sách (TV2). Layout: res/layout/fragment_explore_list.xml */
class ExploreListFragment : Fragment(R.layout.fragment_explore_list) {

    private var _binding: FragmentExploreListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: KeoAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentExploreListBinding.bind(view)
        val nav = findNavController()

        adapter = KeoAdapter { keo ->
            nav.navigate(R.id.action_exploreList_to_lobby, bundleOf(LobbyFragment.ARG_KEO_ID to keo.id))
        }
        binding.rvKeo.adapter = adapter

        binding.empty.ivEmptyIcon.setImageResource(R.drawable.ic_search_24)
        binding.empty.tvEmptyTitle.setText(R.string.explore_empty_title)
        binding.empty.tvEmptyMessage.setText(R.string.explore_empty_message)

        binding.header.setup(
            toggleText = R.string.explore_show_map,
            toggleIcon = R.drawable.ic_map_24,
            onToggle = { nav.popBackStack() },
            onOpenFilter = { nav.navigate(R.id.action_exploreList_to_filter) },
            onScanInvite = { nav.navigate(R.id.action_exploreList_to_scanQr) },
            onSportChanged = ::showKeos,
        )
        binding.fabCreate.setOnClickListener { nav.navigate(R.id.action_exploreList_to_createKeo) }

        showKeos(sport = null)
    }

    /** Lọc dữ liệu mẫu theo môn; không còn kèo nào thì hiện trạng thái rỗng. */
    private fun showKeos(sport: Sport?) {
        val items = SampleData.nearbyKeos.filter { sport == null || it.sport == sport }
        adapter.submitList(items)
        binding.empty.root.isVisible = items.isEmpty()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
