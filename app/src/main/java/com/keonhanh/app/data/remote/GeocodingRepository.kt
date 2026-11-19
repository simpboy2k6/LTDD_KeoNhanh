package com.keonhanh.app.data.remote

import com.keonhanh.app.R
import com.keonhanh.app.data.repository.AppException
import com.keonhanh.app.data.repository.UserLocation
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.TimeUnit

data class PlaceResult(
    val name: String,
    val lat: Double,
    val lng: Double
)

class GeocodingRepository {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val api: GeocodingApi = Retrofit.Builder()
        .baseUrl("https://nominatim.openstreetmap.org/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GeocodingApi::class.java)

    suspend fun searchAddress(
        rawQuery: String,
        userLocation: UserLocation? = null
    ): List<PlaceResult> {
        val query = rawQuery.trim()
        if (query.length < 3) {
            throw AppException(R.string.error_address_too_short)
        }

        // Mặc định lấy tâm Đà Nẵng (16.0544, 108.2022) nếu máy chưa bật GPS
        val centerLat = userLocation?.lat ?: 16.0544
        val centerLng = userLocation?.lng ?: 108.2022

        // Tạo khung viewbox (minLon, maxLat, maxLon, minLat) bán kính ~22km quanh Đà Nẵng / vị trí user
        val delta = 0.2
        val viewbox = "${centerLng - delta},${centerLat + delta},${centerLng + delta},${centerLat - delta}"

        return try {
            var response = api.searchPlaces(
                query = query,
                viewbox = viewbox,
                bounded = 1
            )
        // Nếu không có kết quả quanh vị trí hiện tại (ví dụ máy ảo đang để GPS ở Mỹ), tìm lại toàn quốc bỏ bounded
            if (response.isEmpty()) {
                response = api.searchPlaces(
                    query = query,
                    viewbox = viewbox,
                    bounded = null
                )
            }
            if (response.isEmpty()) {
                throw AppException(R.string.error_address_not_found)
            }

            val refLat = userLocation?.lat ?: 16.0544
            val refLng = userLocation?.lng ?: 108.2022
            val refLoc = userLocation ?: UserLocation(lat = refLat, lng = refLng, accuracyM = 0)

            response.mapNotNull { dto ->
                val lat = dto.lat.toDoubleOrNull()
                val lng = dto.lon.toDoubleOrNull()
                if (lat != null && lng != null) {
                    val distKm = refLoc.distanceTo(lat, lng) / 1000.0
                    val displayNameWithDist = String.format(Locale("vi", "VN"), "%s · %.1f km", dto.display_name, distKm)
                    PlaceResult(name = displayNameWithDist, lat = lat, lng = lng)
                } else null
            }.sortedBy { place ->
                refLoc.distanceTo(place.lat, place.lng)
            }
        } catch (e: AppException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 403 || e.code() == 429) {
                throw AppException(R.string.error_geocoding_busy)
            } else {
                throw AppException(R.string.error_generic)
            }
        } catch (e: SocketTimeoutException) {
            android.util.Log.e("Geocoding", "Timeout sau 15s", e)
            throw AppException(R.string.error_network)
        } catch (e: IOException) {
            android.util.Log.e("Geocoding", "Lỗi mạng chi tiết: ${e.message}", e)
            throw AppException(R.string.error_network)

        } catch (e: Exception) {
            throw AppException(R.string.error_generic)
        }
    }
}