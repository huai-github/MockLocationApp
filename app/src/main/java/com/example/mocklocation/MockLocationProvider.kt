package com.example.mocklocation

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock

/**
 * 封装 Android 官方模拟位置 API。
 * 使用 LocationManager 的 GPS 测试提供器注入坐标，属于系统内置开发者功能，
 * 不依赖 Root，也不包含任何绕过模拟检测的逻辑。
 */
class MockLocationProvider(private val ctx: Context) {

    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val PROVIDER = LocationManager.GPS_PROVIDER

    /** 创建并启用 GPS 测试提供器 */
    fun enable() {
        if (lm.allProviders.contains(PROVIDER)) {
            try { lm.removeTestProvider(PROVIDER) } catch (_: Exception) {}
        }
        lm.addTestProvider(
            PROVIDER,
            false, false, false, false,
            true, true, true,
            Criteria.POWER_LOW,
            Criteria.ACCURACY_FINE
        )
        lm.setTestProviderEnabled(PROVIDER, true)
    }

    /** 向系统注入一个 GPS 坐标 */
    fun setLocation(lat: Double, lng: Double) {
        val loc = Location(PROVIDER).apply {
            latitude = lat
            longitude = lng
            altitude = 0.0
            accuracy = 0.1f
            time = System.currentTimeMillis()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            bearing = 0f
            speed = 0f
        }
        lm.setTestProviderLocation(PROVIDER, loc)
    }

    /** 关闭并移除测试提供器，恢复真实定位 */
    fun disable() {
        try {
            lm.setTestProviderEnabled(PROVIDER, false)
            lm.removeTestProvider(PROVIDER)
        } catch (_: Exception) {}
    }
}
