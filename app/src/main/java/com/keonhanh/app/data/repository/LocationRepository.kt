package com.keonhanh.app.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.keonhanh.app.R
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Vị trí người dùng (Minh Khoa, tuần 4). Một chỗ đọc GPS cho cả app:
 *  - Khám phá S09/S10 (Huy Hoàng): khoảng cách tới từng kèo, lọc theo bán kính
 *  - Chọn vị trí S08 (Huy Hoàng): nút "Vị trí của tôi"
 *  - Check-in S15 (Minh Khoa): khoảng cách tới điểm hẹn + sai số GPS -> CheckInState
 *
 * Repository chỉ KIỂM TRA quyền. XIN quyền phải làm ở Fragment (registerForActivityResult),
 * vì cần Activity để hiện hộp thoại hỏi người dùng.
 *
 * Cần thư viện play-services-location (PR tuan4/hao-thu-vien) và quyền vị trí trong AndroidManifest.
 */

/** Một lần đo: tọa độ và sai số (mét, càng nhỏ càng chính xác). */
data class UserLocation(val lat: Double, val lng: Double, val accuracyM: Int) {
    /** Khoảng cách (mét) từ chỗ mình đứng tới một điểm, ví dụ điểm hẹn của kèo. */
    fun distanceTo(lat: Double, lng: Double): Double = distanceMeters(this.lat, this.lng, lat, lng)
}

interface LocationRepository {
    /** Đã được cấp quyền vị trí (chính xác hoặc gần đúng) chưa. */
    fun hasPermission(): Boolean

    /** Người dùng có đang bật Vị trí trong cài đặt máy không. */
    fun isLocationEnabled(): Boolean

    /**
     * Đo vị trí mới, mất khoảng 1-10 giây. Dùng cho check-in và nút "Vị trí của tôi".
     * Ném [AppException] khi chưa có quyền, đang tắt Vị trí, hoặc đo không ra.
     */
    suspend fun getCurrentLocation(): UserLocation

    /**
     * Vị trí gần nhất máy đã biết, trả về gần như ngay; null nếu chưa có quyền hoặc máy chưa từng đo.
     * Dùng cho Khám phá: đủ để hiện "cách 1,2 km" mà không bắt người dùng chờ GPS.
     */
    suspend fun getLastKnownLocation(): UserLocation?
}

class FusedLocationRepository(private val context: Context) : LocationRepository {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    override fun hasPermission(): Boolean =
        granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
                granted(Manifest.permission.ACCESS_COARSE_LOCATION)

    override fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    @SuppressLint("MissingPermission") // đã kiểm tra bằng hasPermission() ở dòng đầu
    override suspend fun getCurrentLocation(): UserLocation {
        if (!hasPermission()) throw AppException(R.string.error_location_permission)
        if (!isLocationEnabled()) throw AppException(R.string.error_location_off)

        val cancel = CancellationTokenSource()
        val location = try {
            withTimeoutOrNull(TIMEOUT_MS) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.token).await()
            }
        } catch (e: SecurityException) {
            // Người dùng vừa thu hồi quyền trong Cài đặt trong lúc app đang chạy
            throw AppException(R.string.error_location_permission)
        } finally {
            cancel.cancel() // hết giờ chờ thì dừng GPS, đỡ tốn pin
        }
        return location?.toUserLocation() ?: throw AppException(R.string.error_location_unavailable)
    }

    @SuppressLint("MissingPermission")
    override suspend fun getLastKnownLocation(): UserLocation? {
        if (!hasPermission()) return null
        return try {
            client.lastLocation.await()?.toUserLocation()
        } catch (e: SecurityException) {
            null
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun Location.toUserLocation() =
        UserLocation(lat = latitude, lng = longitude, accuracyM = accuracy.toInt())

    private companion object {
        /** Quá 15 giây chưa đo được (trong nhà, máy yếu) thì báo lỗi thay vì quay mãi. */
        const val TIMEOUT_MS = 15_000L
    }
}

/**
 * Khoảng cách theo đường chim bay (mét) giữa hai tọa độ, công thức Haversine.
 * Viết bằng Kotlin thuần thay cho Location.distanceBetween để chạy được trong unit test
 * (test chạy trên máy tính, không có Android).
 */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(a))
}

private const val EARTH_RADIUS_M = 6_371_000.0