package com.liverecorder.app.platform

import android.util.Log
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HuyaAdapter @Inject constructor(
    private val okHttpClient: OkHttpClient
) : PlatformAdapter {

    override val platformName = "虎牙"
    override val platformId = "huya"
    override val loginUrl = "https://www.huya.com/login"

    override fun matchUrl(url: String): Boolean {
        return url.contains("huya.com")
    }

    override suspend fun getRoomId(url: String): String {
        val regex = Regex("""huya\.com/(\d+)""")
        return regex.find(url)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("无法从URL中提取虎牙房间号")
    }

    override suspend fun isLive(roomId: String, cookie: String?): Boolean {
        return try {
            val builder = Request.Builder()
                .url("https://mp.huya.com/cache.php?m=Live&do=profileRoom&pid=$roomId")
                .header("User-Agent", USER_AGENT)
                .get()
            cookie?.let { builder.header("Cookie", it) }
            val request = builder.build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                Log.d(TAG, "虎牙直播状态响应: code=${response.code}, body=${bodyStr?.take(300)}")
                val json = JsonParser.parseString(bodyStr)
                val status = json.asJsonObject.get("status")?.asInt ?: -1
                if (status == 200) {
                    val data = json.asJsonObject.getAsJsonObject("data")
                    val eLiveStatus = data.get("eLiveStatus")?.asInt ?: 0
                    Log.d(TAG, "虎牙直播状态: room=$roomId, eLiveStatus=$eLiveStatus")
                    eLiveStatus == 2
                } else {
                    Log.w(TAG, "虎牙直播状态查询失败: status=$status")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "虎牙直播状态异常", e)
            false
        }
    }

    override suspend fun getStreamUrl(roomId: String, quality: String, cookie: String?): String? {
        return try {
            val builder = Request.Builder()
                .url("https://mp.huya.com/cache.php?m=Live&do=profileRoom&pid=$roomId")
                .header("User-Agent", USER_AGENT)
                .get()
            cookie?.let { builder.header("Cookie", it) }
            val request = builder.build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                Log.d(TAG, "虎牙流URL响应: code=${response.code}, body=${bodyStr?.take(500)}")
                val json = JsonParser.parseString(bodyStr)
                val status = json.asJsonObject.get("status")?.asInt ?: -1
                if (status == 200) {
                    val data = json.asJsonObject.getAsJsonObject("data")
                    val liveData = data.getAsJsonObject("liveData")
                    if (liveData == null) {
                        Log.w(TAG, "虎牙流URL: liveData为空")
                        return@use null
                    }
                    val gameLiveUrl = liveData.get("gameLiveUrl")?.asString
                    if (!gameLiveUrl.isNullOrEmpty()) {
                        Log.d(TAG, "虎牙流URL获取成功(gameLiveUrl): ${gameLiveUrl.take(100)}...")
                        return@use gameLiveUrl
                    }
                    // 尝试从 stream 中获取
                    val streamInfo = liveData.getAsJsonObject("stream")
                    if (streamInfo != null) {
                        val flv = streamInfo.get("flv")?.asString
                            ?: streamInfo.get("hls")?.asString
                        if (flv != null) {
                            Log.d(TAG, "虎牙流URL获取成功(stream): ${flv.take(100)}...")
                            return@use flv
                        }
                    }
                    Log.w(TAG, "虎牙流URL: 无可用流地址")
                    null
                } else {
                    Log.e(TAG, "虎牙流URL失败: status=$status")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "虎牙流URL异常", e)
            null
        }
    }

    override suspend fun getRoomTitle(roomId: String, cookie: String?): String {
        return try {
            val builder = Request.Builder()
                .url("https://mp.huya.com/cache.php?m=Live&do=profileRoom&pid=$roomId")
                .header("User-Agent", USER_AGENT)
                .get()
            cookie?.let { builder.header("Cookie", it) }
            val request = builder.build()

            okHttpClient.newCall(request).execute().use { response ->
                val json = JsonParser.parseString(response.body?.string())
                val status = json.asJsonObject.get("status").asInt
                if (status == 200) {
                    val data = json.asJsonObject.getAsJsonObject("data")
                    val liveData = data.getAsJsonObject("liveData")
                    // 返回主播名称
                    liveData.get("nickname")?.asString
                        ?: liveData.get("introduction")?.asString ?: ""
                } else ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    override suspend fun getLoginQRCode(): QRCodeData? {
        // 虎牙二维码登录 API 暂不支持，回退到浏览器登录
        Log.d(TAG, "虎牙二维码登录暂不支持，请使用浏览器登录")
        return null
    }

    companion object {
        private const val TAG = "HuyaAdapter"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
