package com.keonhanh.app.ui.explore

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.keonhanh.app.R
import com.keonhanh.app.data.model.Keo
import com.keonhanh.app.databinding.FragmentExploreMapBinding
import com.keonhanh.app.ui.common.appViewModels
import com.keonhanh.app.ui.common.bind
import com.keonhanh.app.ui.common.collectWhenStarted
import com.keonhanh.app.ui.lobby.LobbyFragment
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

/** S09 Khám phá – bản đồ (TV2). Layout: res/layout/fragment_explore_map.xml */
class ExploreMapFragment : Fragment(R.layout.fragment_explore_map) {

    private var _binding: FragmentExploreMapBinding? = null
    private val binding get() = _binding!!

    private var myLocationOverlay: org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay? = null

    private val viewModel: ExploreViewModel by appViewModels { container ->
        ExploreViewModel(
            authRepository = container.authRepository,
            keoRepository = container.keoRepository,
            locationRepository = container.locationRepository
        )
    }
    private var hasAskedLocationThisSession = false
    private var hasCenteredMap = false

    private val locationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.refreshLocation()
            centerMapToUserLocation()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentExploreMapBinding.bind(view)
        val nav = findNavController()

        setupMap()

        setFragmentResultListener(FilterBottomSheet.REQUEST_KEY) { _, b ->
            val sport = b.getString(FilterBottomSheet.KEY_SPORT).orEmpty()
            val radius = b.getFloat(FilterBottomSheet.KEY_RADIUS, FilterBottomSheet.DEFAULT_RADIUS_KM)
            val time = b.getString(FilterBottomSheet.KEY_TIME).orEmpty()

            val currentFilter = viewModel.filterFlow.value
            viewModel.updateFilter(
                currentFilter.copy(
                    sport = sport,
                    radiusKm = radius,
                    timeRange = time
                )
            )
        }

        binding.header.setup(
            toggleText = R.string.explore_show_list,
            toggleIcon = R.drawable.ic_format_list_bulleted_24,
            onToggle = { nav.navigate(R.id.action_exploreMap_to_exploreList) },
            onOpenFilter = {
                val f = viewModel.filterFlow.value
                nav.navigate(
                    R.id.action_exploreMap_to_filter,
                    bundleOf(
                        FilterBottomSheet.KEY_SPORT to f.sport,
                        FilterBottomSheet.KEY_RADIUS to f.radiusKm,
                        FilterBottomSheet.KEY_TIME to f.timeRange
                    )
                )
            },
            onScanInvite = { nav.navigate(R.id.action_exploreMap_to_scanQr) },
            onSportChanged = { sport ->
                val currentFilter = viewModel.filterFlow.value
                viewModel.updateFilter(currentFilter.copy(sport = sport?.name ?: ""))
            },
            onSearchChanged = { query ->
                val currentFilter = viewModel.filterFlow.value
                viewModel.updateFilter(currentFilter.copy(query = query))
            }
        )

        binding.fabCreate.setOnClickListener { nav.navigate(R.id.action_exploreMap_to_createKeo) }

        collectWhenStarted(viewModel.keoMapItems) { items ->
            renderKeoMarkers(items)
            if (items.isNotEmpty()) {
                showPreviewCard(items.first().keo)
            } else {
                binding.preview.root.isVisible = false
            }
        }

        collectWhenStarted(viewModel.filterFlow) { filter ->
            binding.header.updateRadius(filter.radiusKm.toInt())
        }

        val container = (requireActivity().application as com.keonhanh.app.KeoNhanhApp).container
        if (!container.locationRepository.hasPermission() && !hasAskedLocationThisSession) {
            hasAskedLocationThisSession = true
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
        binding.fabMyLocation.setOnClickListener {
            val container = (requireActivity().application as com.keonhanh.app.KeoNhanhApp).container
            if (container.locationRepository.hasPermission()) {
                hasCenteredMap = false
                viewModel.refreshLocation()
                centerMapToUserLocation(animate = true)
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun setupMap() {
        val map = binding.mapView
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(14.5)
        centerMapToUserLocation()
        val locOverlay = org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay(
            org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider(requireContext()),
            map
        ).apply {
            enableMyLocation()
        }
        myLocationOverlay = locOverlay
        map.overlays.add(locOverlay)
    }

    private fun centerMapToUserLocation(animate: Boolean = false) {
        if (!hasCenteredMap && !animate) {
            binding.mapView.controller.setCenter(GeoPoint(16.0544, 108.2022)) // Tâm Đà Nẵng
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val container = (requireActivity().application as com.keonhanh.app.KeoNhanhApp).container
            val loc = container.locationRepository.getLastKnownLocation()
                ?: runCatching { container.locationRepository.getCurrentLocation() }.getOrNull()
            if (loc != null && _binding != null) {
                val target = GeoPoint(loc.lat, loc.lng)
                if (animate) binding.mapView.controller.animateTo(target)
                else binding.mapView.controller.setCenter(target)
                hasCenteredMap = true
            }
        }
    }

    private fun renderKeoMarkers(items: List<KeoMapItem>) {
        val map = binding.mapView
        map.overlays.clear()
        myLocationOverlay?.let { map.overlays.add(it) }

        items.forEach { item ->
            val keo = item.keo
            val marker = Marker(map).apply {
                position = GeoPoint(item.lat, item.lng)
                title = keo.title
                snippet = keo.place
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ ->
                    showPreviewCard(keo)
                    map.controller.animateTo(position)
                    true
                }
            }
            map.overlays.add(marker)
        }
        map.invalidate()
    }
    private fun showPreviewCard(selected: Keo) {
        val nav = findNavController()
        binding.preview.root.isVisible = true
        binding.preview.bind(selected, showViewButton = true)

        val openLobby = View.OnClickListener {
            nav.navigate(
                R.id.action_exploreMap_to_lobby,
                bundleOf(LobbyFragment.ARG_KEO_ID to selected.id),
            )
        }
        binding.preview.root.setOnClickListener(openLobby)
        binding.preview.btnViewKeo.setOnClickListener(openLobby)
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        viewModel.refreshLocation()
        if (!hasCenteredMap) {
            centerMapToUserLocation()
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}