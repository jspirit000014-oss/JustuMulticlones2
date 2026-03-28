package top.niunaijun.blackboxa.data

import android.content.Context
import android.content.SharedPreferences

class ProxyRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("proxy_prefs", Context.MODE_PRIVATE)

    fun setProxy(userId: Int, host: String, port: Int) {
        prefs.edit()
            .putString("proxy_host_$userId", host)
            .putInt("proxy_port_$userId", port)
            .apply()
    }

    fun getProxy(userId: Int): Pair<String, Int>? {
        val host = prefs.getString("proxy_host_$userId", null) ?: return null
        val port = prefs.getInt("proxy_port_$userId", 0)
        return Pair(host, port)
    }

    fun clearProxy(userId: Int) {
        prefs.edit()
            .remove("proxy_host_$userId")
            .remove("proxy_port_$userId")
            .apply()
    }

    fun clearAllData(userId: Int, context: Context) {
        clearProxy(userId)
        try {
            context.cacheDir.deleteRecursively()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun spoofDevice(userId: Int, androidId: String, deviceModel: String) {
        prefs.edit()
            .putString("android_id_$userId", androidId)
            .putString("device_model_$userId", deviceModel)
            .apply()
    }

    fun getSpoofedDevice(userId: Int): Pair<String, String>? {
        val androidId = prefs.getString("android_id_$userId", null) ?: return null
        val model = prefs.getString("device_model_$userId", "") ?: ""
        return Pair(androidId, model)
    }
}
