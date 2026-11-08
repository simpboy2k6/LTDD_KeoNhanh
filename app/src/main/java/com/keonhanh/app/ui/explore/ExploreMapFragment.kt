package com.keonhanh.app.ui.explore

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.keonhanh.app.R
import com.keonhanh.app.data.sample.SampleData
import com.keonhanh.app.databinding.FragmentExploreMapBinding
import com.keonhanh.app.ui.common.bind
import com.keonhanh.app.ui.lobby.LobbyFragment

/** S09 Khám phá – bản đồ (TV2). Layout: res/layout/fragment_explore_map.xml */
class ExploreMapFragment : Fragment(R.layout.fragment_explore_map) {

    private var _binding: FragmentExploreMapBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentExploreMapBinding.bind(view)
        val nav = findNavController()

        binding.header.setup(
            toggleText = R.string.explore_show_list,
            toggleIcon = R.drawable.ic_format_list_bulleted_24,
            onToggle = { nav.navigate(R.id.action_exploreMap_to_exploreList) },
            onOpenFilter = { nav.navigate(R.id.action_exploreMap_to_filter) },
            onScanInvite = { nav.navigate(R.id.action_exploreMap_to_scanQr) },
        )

        // Tuần 2: thẻ xem trước luôn là kèo gần nhất. Tuần 3: đổi theo marker được bấm trên MapView.
        val selected = SampleData.nearbyKeos.first { it.id == "k1" }
        binding.preview.bind(selected, showViewButton = true)
        val openLobby = View.OnClickListener {
            nav.navigate(
                R.id.action_exploreMap_to_lobby,
                bundleOf(LobbyFragment.ARG_KEO_ID to selected.id),
            )
        }
        binding.preview.root.setOnClickListener(openLobby)
        binding.preview.btnViewKeo.setOnClickListener(openLobby)

        binding.fabCreate.setOnClickListener { nav.navigate(R.id.action_exploreMap_to_createKeo) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
