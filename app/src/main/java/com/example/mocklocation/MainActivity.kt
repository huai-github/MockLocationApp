package com.example.mocklocation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.LocationManager
import android.location.Location
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import android.widget.AdapterView
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

    // 收藏夹
    private lateinit var favoritesManager: FavoritesManager
    private lateinit var spinner: Spinner
    private lateinit var favAdapter: ArrayAdapter<String>
    private var favorites: List<Favorite> = emptyList()
    private var refreshingSpinner = false

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
        binding.btnSelfTest.setOnClickListener { selfTest() }
        binding.btnFavorite.setOnClickListener { saveFavorite() }

        // 收藏夹初始化
        favoritesManager = FavoritesManager(this)
        spinner = binding.spinnerFavorites
        favAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf())
        favAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = favAdapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, view: android.view.View?, position: Int, id: Long
            ) {
                if (refreshingSpinner) return
                if (position in favorites.indices) {
                    val f = favorites[position]
                    setSelected(GeoPoint(f.lat, f.lng))
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        // 长按收藏项可删除
        spinner.setOnLongClickListener {
            val pos = spinner.selectedItemPosition
            if (pos in favorites.indices) {
                favoritesManager.removeAt(pos)
                refreshFavorites()
                Toast.makeText(this, R.string.toast_fav_deleted, Toast.LENGTH_SHORT).show()
                true
            } else false
        }
        refreshFavorites()

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

    /**
     * 工具自检：验证系统是否真的接收了我们注入的 mock 坐标。
     * 先检查模拟位置权限，再临时注入一个明显的测试坐标，用 getLastKnownLocation 读回确认。
     * 若服务正在运行，自检后重启服务恢复真实注入，避免干扰。
     */
    private fun selfTest() {
        if (!ensurePermission()) {
            binding.textDiagnostics.text = "需要定位权限才能自检，请先授权。"
            return
        }
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val sb = StringBuilder()
        val mockOk = canMock()
        sb.appendLine(
            if (mockOk) "① 模拟位置权限：已授权（已在开发者选项中选为本应用）"
            else "① 模拟位置权限：未授权（去 开发者选项 → 模拟位置信息应用 → 选本应用）"
        )
        if (!mockOk) {
            binding.textDiagnostics.text = sb.toString()
            openDevSettings()
            return
        }

        val testLat = 0.123456
        val testLng = 0.654321
        try {
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                try { lm.removeTestProvider(p) } catch (_: Exception) {}
                try {
                    lm.addTestProvider(
                        p, false, false, false, false,
                        true, true, true,
                        Criteria.POWER_LOW, Criteria.ACCURACY_FINE
                    )
                    lm.setTestProviderEnabled(p, true)
                } catch (_: Exception) {}
            }
            val loc = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = testLat
                longitude = testLng
                altitude = 0.0
                accuracy = 0.1f
                time = System.currentTimeMillis()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
                bearing = 0f
                speed = 0f
            }
            lm.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc)

            val back = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (back != null &&
                Math.abs(back.latitude - testLat) < 0.001 &&
                Math.abs(back.longitude - testLng) < 0.001
            ) {
                val latStr = "%.4f".format(back.latitude)
                val lngStr = "%.4f".format(back.longitude)
                sb.appendLine(
                    "② 系统接收 mock：成功（lat=$latStr, lng=$lngStr, isFromMock=${back.isFromMockProvider()}）"
                )
                sb.appendLine("结论：本工具注入功能正常。")
                sb.appendLine("· 不检测 mock 的 App（如 Google 地图、浏览器定位）会读到虚拟位置。")
                sb.appendLine("· 高德 / M3 会检测 isFromMock=true 并丢弃，回退真实定位（其主动反作弊，本工具不做绕过）。")
            } else {
                sb.appendLine("② 系统接收 mock：未读回（lastKnown 为 null 或数值不符）。")
                sb.appendLine("可能原因：ROM 限制（部分国产系统不允许注入），或定位权限未授予。")
            }
        } catch (e: Exception) {
            sb.appendLine("② 自检异常：${e.message}")
        } finally {
            if (running) {
                stopService(Intent(this, MockLocationService::class.java))
                val point = selectedPoint
                if (point != null) {
                    val intent = Intent(this, MockLocationService::class.java).apply {
                        putExtra("lat", point.latitude)
                        putExtra("lng", point.longitude)
                    }
                    ContextCompat.startForegroundService(this, intent)
                }
            } else {
                for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                    try { lm.setTestProviderEnabled(p, false); lm.removeTestProvider(p) } catch (_: Exception) {}
                }
            }
        }
        binding.textDiagnostics.text = sb.toString()
    }

    /** 收藏当前选中的点 */
    private fun saveFavorite() {
        val point = selectedPoint
        if (point == null) {
            Toast.makeText(this, R.string.toast_fav_pick_first, Toast.LENGTH_SHORT).show()
            return
        }
        val name = "点 ${favorites.size + 1}"
        favoritesManager.add(Favorite(name, point.latitude, point.longitude))
        refreshFavorites()
        Toast.makeText(this, R.string.toast_fav_saved, Toast.LENGTH_SHORT).show()
    }

    /** 重新加载收藏列表到下拉框 */
    private fun refreshFavorites() {
        favorites = favoritesManager.getAll()
        val items = if (favorites.isEmpty()) {
            mutableListOf(getString(R.string.spinner_favorites_hint))
        } else {
            favorites.map {
                "${it.name}  ${"%.5f".format(it.lat)},${"%.5f".format(it.lng)}"
            }.toMutableList()
        }
        refreshingSpinner = true
        favAdapter.clear()
        favAdapter.addAll(items)
        favAdapter.notifyDataSetChanged()
        spinner.post { refreshingSpinner = false }
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
