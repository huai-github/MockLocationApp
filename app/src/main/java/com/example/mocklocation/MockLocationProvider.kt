package com.example.mocklocation

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock

/**
 * 封装 Android 官方模拟位置 API。
 * 使用 LocationManager 的 GPS 与 NETWORK 测试提供器注入坐标，属于系统内置开发者功能，
 * 不依赖 Root，也不包含任何绕过模拟检测的逻辑。
 * 同时覆盖两个提供器，可让更多“不检测 mock”的 App（如浏览器 HTML5 定位、部分网络定位 App）读到虚拟位置。
 */
class MockLocationProvider(private val ctx: Context) {

    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val PROVIDERS = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER
    )

    /** 创建并启用测试提供器（GPS + NETWORK） */
    fun enable() {
        for (p in PROVIDERS) {
            try {
                if (lm.allProviders.contains(p)) {
                    try { lm.removeTestProvider(p) } catch (_: Exception) {}
                }
                lm.addTestProvider(
                    p,
                    false, false, false, false,
                    true, true, true,
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE
                )
                lm.setTestProviderEnabled(p, true)
            } catch (_: Exception) {
                // 个别 ROM 可能不允许为某个 provider 添加测试提供器，跳过该 provider
            }
        }
    }

    /** 向系统注入一个坐标（GPS 与 NETWORK 同时注入） */
    fun setLocation(lat: Double, lng: Double) {
        for (p in PROVIDERS) {
            try {
                val loc = Location(p).apply {
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
                lm.setTestProviderLocation(p, loc)
            } catch (_: Exception) {
                // 单个 provider 注入失败不影响另一个
            }
        }
    }

    /** 关闭并移除测试提供器，恢复真实定位 */
    fun disable() {
        for (p in PROVIDERS) {
            try {
                lm.setTestProviderEnabled(p, false)
                lm.removeTestProvider(p)
            } catch (_: Exception) {}
        }
    }
}
