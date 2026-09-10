package com.liverecorder.app.platform

import android.util.Log
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DouyuAdapter @Inject constructor(
    private val okHttpClient: OkHttpClient
) : PlatformAdapter {

    override val platformName = "斗鱼"
    override val platformId = "douyu"
    override val loginUrl = "https://www.douyu.com/join/login"

    override fun matchUrl(url: String): Boolean {
        return url.contains("douyu.com")
    }

    override suspend fun getRoomId(url: String): String {
        val regex = Regex("""douyu\.com/(\d+)""")
        return regex.find(url)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("无法从URL中提取斗鱼房间号")
    }

    override suspend fun isLive(roomId: String, cookie: String?): Boolean {
        return try {
            val builder = Request.Builder()
                .url("https://open.douyucdn.cn/api/RoomApi/room/$roomId")
                .header("User-Agent", USER_AGENT)
                .get()
            cookie?.let { builder.header("Cookie", it) }
            val request = builder.build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                Log.d(TAG, "斗鱼直播状态响应: body=${bodyStr?.take(300)}")
                val json = JsonParser.parseString(bodyStr)
                val error = json.asJsonObject.get("error").asInt
                if (error == 0) {
                    val data = json.asJsonObject.getAsJsonObject("data")
                    val roomStatus = data.get("room_status")?.asString
                    Log.d(TAG, "斗鱼直播状态: room=$roomId, status=$roomStatus")
                    roomStatus == "on"
                } else {
                    Log.w(TAG, "斗鱼直播状态查询失败: error=$error")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "斗鱼直播状态异常", e)
            false
        }
    }

    override suspend fun getStreamUrl(roomId: String, quality: String, cookie: String?): String? {
        return try {
            // 尝试移动端 API
            val builder = Request.Builder()
                .url("https://m.douyu.com/api/room/ratestream?rid=$roomId")
                .header("User-Agent", MOBILE_USER_AGENT)
                .get()
            cookie?.let { builder.header("Cookie", it) }
            val request = builder.build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                Log.d(TAG, "斗鱼流URL响应(mobile): code=${response.code}, body=${bodyStr?.take(500)}")
                val json = JsonParser.parseString(bodyStr)
                val code = json.asJsonObject.get("code").asInt
                if (code == 0) {
                    val data = json.asJsonObject.getAsJsonObject("data")
                    // data 可能是对象或数组
                    if (data.isJsonObject) {
                        val url = data.get("url")?.asString
                        if (url != null) {
                            Log.d(TAG, "斗鱼流URL获取成功: ${url.take(100)}...")
                            return@use url
                        }
                    }
                    // 尝试从 data 中获取 rtmp_url + rtmp_live
                    val rtmpUrl = data.get("rtmp_url")?.asString
                    val rtmpLive = data.get("rtmp_live")?.asString
                    if (rtmpUrl != null && rtmpLive != null) {
                        val fullUrl = "$rtmpUrl/$rtmpLive"
                        Log.d(TAG, "斗鱼流URL获取成功(rtmp): ${fullUrl.take(100)}...")
                        return@use fullUrl
                    }
                    Log.w(TAG, "斗鱼流URL: data中无url字段, data=$data")
                    null
                } else {
                    val msg = json.asJsonObject.get("msg")?.asString ?: "unknown"
                    Log.e(TAG, "斗鱼流URL失败(mobile): code=$code, msg=$msg")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "斗鱼流URL异常", e)
            null
        }
    }

    override suspend fun getRoomTitle(roomId: String, cookie: String?): String {
        return try {
            val builder = Request.Builder()
                .url("https://open.douyucdn.cn/api/RoomApi/room/$roomId")
                .header("User-Agent", USER_AGENT)
                .get()
            cookie?.let { builder.header("Cookie", it) }
            val request = builder.build()

            okHttpClient.newCall(request).execute().use { response ->
                val json = JsonParser.parseString(response.body?.string())
                val error = json.asJsonObject.get("error").asInt
                if (error == 0) {
                    val data = json.asJsonObject.getAsJsonObject("data")
                    // 返回主播名称
                    data.get("owner_name")?.asString
                        ?: data.get("room_name")?.asString ?: ""
                } else ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    override suspend fun getLoginQRCode(): QRCodeData? {
        // 斗鱼二维码登录 API 较复杂，需要加密参数，暂不支持
        // 回退到浏览器登录
        Log.d(TAG, "斗鱼二维码登录暂不支持，请使用浏览器登录")
        return null
    }

    companion object {
        private const val TAG = "DouyuAdapter"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
