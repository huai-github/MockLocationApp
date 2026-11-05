package com.example.mocklocation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 前台服务：持续把选定坐标注入系统 GPS 提供器。
 * 每 1 秒重新注入一次，保证坐标稳定持续生效（部分 App 会丢弃单次定位）。
 */
class MockLocationService : Service() {

    private lateinit var provider: MockLocationProvider
    private var lat = 0.0
    private var lng = 0.0
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        provider = MockLocationProvider(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lat = intent?.getDoubleExtra("lat", 0.0) ?: 0.0
        lng = intent?.getDoubleExtra("lng", 0.0) ?: 0.0

        startForeground(NOTIF_ID, buildNotification())
        provider.enable()
        provider.setLocation(lat, lng)

        job = scope.launch {
            while (isActive) {
                delay(1000)
                provider.setLocation(lat, lng)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        provider.disable()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channelId = "mock_location_channel"
        val title = "虚拟定位进行中"
        val text = "经度: $lng, 纬度: $lat"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                "虚拟定位",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
            Notification.Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher)
                .build()
        }
    }

    companion object {
        const val NOTIF_ID = 1
    }
}
