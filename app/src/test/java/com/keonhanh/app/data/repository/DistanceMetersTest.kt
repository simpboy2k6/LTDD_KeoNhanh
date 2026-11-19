package com.keonhanh.app.data.repository

import com.keonhanh.app.data.model.CheckInRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test cho hàm tính khoảng cách trong LocationRepository.kt (Minh Khoa, tuần 4).
 * Đặt ở: app/src/test/java/com/keonhanh/app/data/repository/DistanceMetersTest.kt
 * Chạy trên máy tính, không cần máy ảo hay Firebase.
 */
class DistanceMetersTest {

    @Test
    fun samePoint_isZero() {
        // Đứng đúng điểm hẹn thì khoảng cách phải bằng 0
        assertEquals(0.0, distanceMeters(16.0544, 108.2022, 16.0544, 108.2022), 0.001)
    }

    @Test
    fun oneDegreeLatitude_isAbout111km() {
        // 1 độ vĩ = 2 * pi * 6371 km / 360 ≈ 111,195 km. Sai công thức (quên đổi sang radian) là lệch rất xa
        assertEquals(111_195.0, distanceMeters(21.0, 105.0, 22.0, 105.0), 5.0)
    }

    @Test
    fun isSymmetric() {
        // Từ A tới B phải bằng từ B tới A; nếu khác là truyền nhầm thứ tự tham số
        val ab = distanceMeters(21.0285, 105.8542, 21.0245, 105.8576)
        val ba = distanceMeters(21.0245, 105.8576, 21.0285, 105.8542)
        assertEquals(ab, ba, 0.001)
    }

    @Test
    fun userLocation_distanceTo_matchesFunction() {
        // Hàm tiện ích Hoàng dùng ở Khám phá phải cho cùng kết quả với hàm gốc
        val me = UserLocation(lat = 21.0285, lng = 105.8542, accuracyM = 10)
        assertEquals(
            distanceMeters(21.0285, 105.8542, 21.0245, 105.8576),
            me.distanceTo(21.0245, 105.8576),
            0.001,
        )
    }

    @Test
    fun checkInRadius_insideAndOutside100m() {
        // Đúng tình huống check-in: điểm hẹn ở Đà Nẵng, người chơi đứng lệch lên phía bắc
        val hen = UserLocation(lat = 16.0544, lng = 108.2022, accuracyM = 0)
        val gan = hen.distanceTo(16.0552, 108.2022) // lệch 0.0008 độ ≈ 89 m
        val xa = hen.distanceTo(16.0554, 108.2022)  // lệch 0.0010 độ ≈ 111 m
        assertTrue("89 m phải trong bán kính check-in", gan <= CheckInRules.RADIUS_M)
        assertTrue("111 m phải ngoài bán kính check-in", xa > CheckInRules.RADIUS_M)
    }
}