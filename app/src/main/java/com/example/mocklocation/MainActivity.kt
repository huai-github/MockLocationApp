package com.example.mocklocation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mocklocation.databinding.ActivityMainBinding
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var map: MapView
    private var selectedPoint: GeoPoint? = null
    private var marker: Marker? = null
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // OSMDroid 必须在创建 MapView 前完成初始化
        val osmConfig = Configuration.getInstance()
        osmConfig.userAgentValue = packageName
        // 瓦片缓存放到应用私有目录：无需外部存储权限，API29+ 也兼容，
        // 避免默认路径不可写导致瓦片静默失败（表现为地图只剩网格）
        val osmBase = File(cacheDir, "osmdroid")
        osmBase.mkdirs()
        osmConfig.osmdroidBasePath = osmBase
        osmConfig.osmdroidTileCache = File(osmBase, "tiles")
        osmConfig.load(this, getPreferences(Context.MODE_PRIVATE))

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        map = binding.mapView
        // 显式指定瓦片源（OpenStreetMap 标准瓦片）
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        map.controller.setCenter(GeoPoint(39.9042, 116.4074)) // 默认北京

        // 点击地图选点
        val tapOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let { setSelected(it) }
                return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean = false
        })
        map.overlays.add(tapOverlay)

        binding.btnSearch.setOnClickListener { handleInput() }
        binding.btnToggle.setOnClickListener { toggleMock() }
        binding.btnDevSettings.setOnClickListener { openDevSettings() }

        updateUi()
    }

    /** 在地图上放置标记并记录坐标 */
    private fun setSelected(point: GeoPoint) {
        selectedPoint = point
        marker?.let { map.overlays.remove(it) }
        marker = Marker(map).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "虚拟位置"
        }
        map.overlays.add(marker)
        map.controller.animateTo(point)
        binding.textCoord.text = getString(
            R.string.coord_format,
            "%.6f".format(point.longitude),
            "%.6f".format(point.latitude)
        )
    }

    /** 解析“纬度,经度”输入并定位 */
    private fun handleInput() {
        val text = binding.editSearch.text.toString().trim()
        val parts = text.split(",")
        if (parts.size == 2) {
            val a = parts[0].toDoubleOrNull()
            val b = parts[1].toDoubleOrNull()
            if (a != null && b != null) {
                setSelected(GeoPoint(a, b))
                return
            }
        }
        Toast.makeText(
            this,
            "请输入 纬度,经度，例如 39.9042,116.4074",
            Toast.LENGTH_LONG
        ).show()
    }

    /** 运行时申请定位权限 */
    private fun ensurePermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQ_PERMISSION
            )
            false
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            // 权限已授予，可再次点击“开启”
        }
    }

    /**
     * 探测本应用是否已被设为“模拟位置信息应用”。
     * 直接尝试 addTestProvider；若无权限会抛 SecurityException。
     * 注意：removeTestProvider 只能移除由 addTestProvider 创建的测试提供器，
     * 对真实 GPS 提供器调用会抛 IllegalArgumentException，这里忽略即可。
     */
    private fun canMock(): Boolean {
        return try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val p = LocationManager.GPS_PROVIDER
            try { lm.removeTestProvider(p) } catch (_: Exception) {}
            lm.addTestProvider(
                p, false, false, false, false,
                true, true, true,
                Criteria.POWER_LOW, Criteria.ACCURACY_FINE
            )
            lm.removeTestProvider(p)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun toggleMock() {
        if (running) {
            stopMock()
            return
        }
        if (!ensurePermission()) {
            Toast.makeText(this, R.string.toast_perm_denied, Toast.LENGTH_SHORT).show()
            return
        }
        val point = selectedPoint
        if (point == null) {
            Toast.makeText(this, R.string.toast_pick_first, Toast.LENGTH_SHORT).show()
            return
        }
        if (!canMock()) {
            Toast.makeText(this, R.string.toast_mock_not_set, Toast.LENGTH_LONG).show()
            openDevSettings()
            return
        }
        val intent = Intent(this, MockLocationService::class.java).apply {
            putExtra("lat", point.latitude)
            putExtra("lng", point.longitude)
        }
        ContextCompat.startForegroundService(this, intent)
        running = true
        updateUi()
    }

    private fun stopMock() {
        stopService(Intent(this, MockLocationService::class.java))
        running = false
        updateUi()
    }

    private fun openDevSettings() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.dev_settings_hint, Toast.LENGTH_LONG).show()
        }
    }

    private fun updateUi() {
        binding.btnToggle.text = if (running) {
            getString(R.string.btn_stop)
        } else {
            getString(R.string.btn_start)
        }
        if (running) {
            binding.textCoord.text = getString(R.string.status_running)
        } else if (selectedPoint != null) {
            binding.textCoord.text = getString(
                R.string.coord_format,
                "%.6f".format(selectedPoint!!.longitude),
                "%.6f".format(selectedPoint!!.latitude)
            )
        } else {
            binding.textCoord.text = getString(R.string.status_idle)
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        // OSMDroid 的 MapView 没有 onDestroy()，无需在此调用
    }

    companion object {
        private const val REQ_PERMISSION = 1
    }
}
