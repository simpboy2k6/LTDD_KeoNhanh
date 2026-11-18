package com.keonhanh.app.data.remote

import com.keonhanh.app.R
import com.keonhanh.app.data.repository.AppException
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory // Nếu nhóm dùng Moshi hoặc Kotlinx Serialization thì báo mình đổi lại nhé
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

data class PlaceResult(
    val name: String,
    val lat: Double,
    val lng: Double
)

class GeocodingRepository {

    // Cấu hình timeout 10 giây theo đúng yêu cầu bảng mục 5
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

    suspend fun searchAddress(rawQuery: String): List<PlaceResult> {
        val query = rawQuery.trim()
        if (query.length < 3) {
            throw AppException(R.string.error_address_too_short)
        }

        return try {
            val response = api.searchPlaces(query = query)
            if (response.isEmpty()) {
                throw AppException(R.string.error_address_not_found)
            }
            response.mapNotNull { dto ->
                val lat = dto.lat.toDoubleOrNull()
                val lng = dto.lon.toDoubleOrNull()
                if (lat != null && lng != null) {
                    PlaceResult(name = dto.display_name, lat = lat, lng = lng)
                } else null
            }
        } catch (e: AppException) {
            throw e
        } catch (e: HttpException) {
            // Bắt lỗi HTTP 403 hoặc 429 khi server chặn request
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