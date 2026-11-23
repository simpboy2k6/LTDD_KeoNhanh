package com.keonhanh.app.ui.create

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.keonhanh.app.R
import com.keonhanh.app.data.model.CheckInRules
import com.keonhanh.app.data.remote.PlaceResult
import com.keonhanh.app.databinding.FragmentPickLocationBinding
import com.keonhanh.app.ui.common.UiState
import com.keonhanh.app.ui.common.appViewModels
import com.keonhanh.app.ui.common.collectWhenStarted
import kotlinx.coroutines.launch
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import java.util.Locale

/** S13 Chọn điểm hẹn (TV2). Layout: res/layout/fragment_pick_location.xml */
class PickLocationFragment : Fragment(R.layout.fragment_pick_location) {

    private var _binding: FragmentPickLocationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PickLocationViewModel by appViewModels { container ->
        PickLocationViewModel(
            geocodingRepository = container.geocodingRepository,
            locationRepository = container.locationRepository
        )
    }

    private var selectedPlace: PlaceResult? = null
    private var ignoreNextScrollEvent = false

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.fetchMyLocation()
        } else {
            Snackbar.make(binding.root, R.string.error_location_permission, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPickLocationBinding.bind(view)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.tvRadiusNote.text = getString(R.string.pick_radius_note, CheckInRules.RADIUS_M)

        setupMap()

        val placeAdapter = PlaceResultAdapter { place ->
            applySelectedPlace(place, moveCamera = true)
            binding.rvSearchResults.isVisible = false
        }
        binding.rvSearchResults.adapter = placeAdapter

        binding.btnSearchAddress.setOnClickListener {
            val query = binding.etSearchAddress.text?.toString().orEmpty()
            viewModel.searchAddress(query)
        }

        binding.fabMyLocation.setOnClickListener {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        binding.btnConfirm.setOnClickListener {
            val place = selectedPlace ?: return@setOnClickListener
            setFragmentResult(
                REQUEST_KEY,
                bundleOf(
                    KEY_PLACE to place.name.substringBefore(" · "),
                    KEY_LAT to place.lat,
                    KEY_LNG to place.lng
                )
            )
            findNavController().navigateUp()
        }

        collectWhenStarted(viewModel.searchState) { state ->
            when (state) {
                is UiState.Loading -> {
                    binding.progressBar.isVisible = true
                    binding.btnSearchAddress.isEnabled = false
                }
                is UiState.Success -> {
                    binding.progressBar.isVisible = false
                    binding.btnSearchAddress.isEnabled = true
                    placeAdapter.submitList(state.data)
                    binding.rvSearchResults.isVisible = state.data.isNotEmpty()
                }
                is UiState.Error -> {
                    binding.progressBar.isVisible = false
                    binding.btnSearchAddress.isEnabled = true
                    binding.rvSearchResults.isVisible = false
                    Snackbar.make(binding.root, state.message.resolve(requireContext()), Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        collectWhenStarted(viewModel.myLocationState) { state ->
            when (state) {
                is UiState.Loading -> {
                    binding.progressBar.isVisible = true
                }
                is UiState.Success -> {
                    binding.progressBar.isVisible = false
                    state.data?.let { place ->
                        applySelectedPlace(place, moveCamera = true)
                    }
                }
                is UiState.Error -> {
                    binding.progressBar.isVisible = false
                    Snackbar.make(binding.root, state.message.resolve(requireContext()), Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupMap() {
        val map = binding.mapView
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(16.5)

        // Đặt tạm tâm Đà Nẵng trước để tránh map nhảy về (0, 0)
        ignoreNextScrollEvent = true
        map.controller.setCenter(GeoPoint(16.0544, 108.2022))

        // Gọi hàm suspend getLastKnownLocation() trong coroutine
        viewLifecycleOwner.lifecycleScope.launch {
            val container = (requireActivity().application as com.keonhanh.app.KeoNhanhApp).container
            val lastLoc = container.locationRepository.getLastKnownLocation()
            if (lastLoc != null && _binding != null) {
                ignoreNextScrollEvent = true
                map.controller.setCenter(GeoPoint(lastLoc.lat, lastLoc.lng))
            }
        }

        map.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                if (ignoreNextScrollEvent) {
                    ignoreNextScrollEvent = false
                    return false
                }
                val center = map.mapCenter
                val picked = PlaceResult(
                    name = String.format(Locale.US, "Điểm đã chọn (%.5f, %.5f)", center.latitude, center.longitude),
                    lat = center.latitude,
                    lng = center.longitude
                )
                applySelectedPlace(picked, moveCamera = false)
                return true
            }

            override fun onZoom(event: ZoomEvent?): Boolean = false
        })
    }

    private fun applySelectedPlace(place: PlaceResult, moveCamera: Boolean = false) {
        selectedPlace = place
        binding.tvPlace.text = place.name
        binding.tvCoords.text = String.format(Locale.US, "%.5f, %.5f", place.lat, place.lng)
        binding.btnConfirm.isEnabled = true
        if (moveCamera) {
            ignoreNextScrollEvent = true
            binding.mapView.controller.animateTo(GeoPoint(place.lat, place.lng))
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroyView() {
        binding.mapView.onDetach()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val REQUEST_KEY = "pick_location"
        const val KEY_PLACE = "place"
        const val KEY_LAT = "lat"
        const val KEY_LNG = "lng"
    }
}

private class PlaceResultAdapter(
    private val onClick: (PlaceResult) -> Unit
) : RecyclerView.Adapter<PlaceResultAdapter.VH>() {

    private var items: List<PlaceResult> = emptyList()

    fun submitList(newItems: List<PlaceResult>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false) as TextView
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tv.text = item.name
        holder.tv.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)
}