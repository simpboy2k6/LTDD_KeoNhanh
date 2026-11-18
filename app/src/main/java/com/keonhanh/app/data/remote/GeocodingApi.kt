package com.keonhanh.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

// Model hứng dữ liệu JSON từ OpenStreetMap Nominatim
data class NominatimPlaceDto(
    val place_id: Long,
    val display_name: String,
    val lat: String,
    val lon: String
)

interface GeocodingApi {
    @GET("search")
    suspend fun searchPlaces(
        @Query("q") query: String,
        @Query("format") format: String = "jsonv2",
        @Query("countrycodes") countryCodes: String = "vn",
        @Query("limit") limit: Int = 5,
        @Query("viewbox") viewbox: String? = null,
        @Query("bounded") bounded: Int = 1,
        // Bắt buộc có User-Agent riêng theo quy định của Nominatim
        @Header("User-Agent") userAgent: String = "KeoNhanh/1.0 (do an LTDD)"
    ): List<NominatimPlaceDto>
}