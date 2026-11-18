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

    // Bộ xin quyền vị trí theo đúng lưu ý trong LocationRepository.kt của Minh Khoa
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

        val placeAdapter = PlaceResultAdapter { place ->
            applySelectedPlace(place)
            binding.rvSearchResults.isVisible = false
        }
        binding.rvSearchResults.adapter = placeAdapter

        // Bấm Tìm mới gọi API Nominatim
        binding.btnSearchAddress.setOnClickListener {
            val query = binding.etSearchAddress.text?.toString().orEmpty()
            viewModel.searchAddress(query)
        }

        // Bấm Vị trí của tôi -> xin quyền và lấy GPS
        binding.fabMyLocation.setOnClickListener {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        // Bấm Xác nhận -> trả cả tên, lat, lng về CreateKeoFragment
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

        // Lắng nghe kết quả tìm địa chỉ
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

        // Lắng nghe kết quả GPS "Vị trí của tôi"
        collectWhenStarted(viewModel.myLocationState) { state ->
            when (state) {
                is UiState.Loading -> {
                    binding.progressBar.isVisible = true
                }
                is UiState.Success -> {
                    binding.progressBar.isVisible = false
                    state.data?.let { place ->
                        applySelectedPlace(place)
                    }
                }
                is UiState.Error -> {
                    binding.progressBar.isVisible = false
                    Snackbar.make(binding.root, state.message.resolve(requireContext()), Snackbar.LENGTH_SHORT).show()
                }
            }
        }
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun applySelectedPlace(place: PlaceResult) {
        selectedPlace = place
        binding.tvPlace.text = place.name
        binding.tvCoords.text = String.format("%.5f, %.5f", place.lat, place.lng)
        binding.btnConfirm.isEnabled = true
    }

    override fun onDestroyView() {
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

// Adapter gọn nhẹ hiển thị danh sách kết quả tìm kiếm
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