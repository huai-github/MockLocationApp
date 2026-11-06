package com.example.mocklocation

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 收藏点管理：用 SharedPreferences 持久化一组经纬度收藏点。
 * 数据仅保存在本应用私有存储，不涉及任何网络或反检测逻辑。
 */
data class Favorite(val name: String, val lat: Double, val lng: Double)

class FavoritesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("mock_favorites", Context.MODE_PRIVATE)
    private val KEY = "list"

    fun getAll(): List<Favorite> {
        val arr = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        val list = mutableListOf<Favorite>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                Favorite(
                    o.getString("name"),
                    o.getDouble("lat"),
                    o.getDouble("lng")
                )
            )
        }
        return list
    }

    fun add(fav: Favorite) {
        val list = getAll().toMutableList()
        list.add(fav)
        save(list)
    }

    fun removeAt(index: Int) {
        val list = getAll().toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            save(list)
        }
    }

    private fun save(list: List<Favorite>) {
        val arr = JSONArray()
        list.forEach { f ->
            arr.put(
                JSONObject().apply {
                    put("name", f.name)
                    put("lat", f.lat)
                    put("lng", f.lng)
                }
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }
}
