package com.liverecorder.app.platform

import android.util.Log
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BilibiliAdapter @Inject constructor(
    private val okHttpClient: OkHttpClient
) : PlatformAdapter {

    override val platformName = "哔哩哔哩"
    override val platformId = "bilibili"
    override val loginUrl = "https://passport.bilibili.com/login"

    override fun matchUrl(url: String): Boolean {
        return url.contains("live.bilibili.com")
    }

    override suspend fun getRoomId(url: String): String {
        val regex = Regex("""live\.bilibili\.com/(\d+)""")
        return regex.find(url)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("无法从URL中提取B站房间号")
    }

    override suspend fun isLive(roomId: String, cookie: String?): Boolean {
        return try {
            val builder = Request.Builder()
                .url("https://api.live.bilibili.com/room/v1/Room/get_info?room_id=$roomId")
                .header("User-Agent", USER_AGENT)
                .get()
            cookie?.let { builder.header("Cookie", it) }
            val request = builder.build()

            okHttpClient.newCall(request).execute().use { response ->
                val json = JsonParser.parseString(response.body?.string())
                val code = json.asJsonObject.get("code").asInt
                if (code == 0) {
                    val liveStatus = json.asJsonObject.getAsJsonObject("data")
                        .get("live_status").asInt
                    Log.d(TAG, "B站直播状态: room=$roomId, liveStatus=$liveStatus")
                    liveStatus == 1
                } else {
                    Log.w(TAG, "B站直播状态查询失败: code=$code")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "B站直播状态异常", e)
            false
        }
    }

    override suspend fun getStreamUrl(roomId: String, quality: String, cookie: String?): String? {
        return try {
            val realRoomId = getRealRoomId(roomId)
            Log.d(TAG, "B站真实房间号: $roomId -> $realRoomId")
            val qualityCode = when (quality) {
                "original" -> "4"
                "high" -> "3"
                "medium" -> "2"
                "low" -> "6"
                else -> "4"
            }
            val url = "https://api.live.bilibili.com/room/v1/Room/playUrl?cid=$realRoomId&qn=$qualityCode&platform=web"
            Log.d(TAG, "B站获取流URL: $url")
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
            cookie?.let { builder.header("Cookie", it) }
            val request = builder.build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                Log.d(TAG, "B站流URL响应: code=${response.code}, body=${bodyStr?.take(500)}")
                val json = JsonParser.parseString(bodyStr)
                val code = json.asJsonObject.get("code").asInt
                if (code == 0) {
                    val data = json.asJsonObject.getAsJsonObject("data")
                    val durl = data.getAsJsonArray("durl")
                    if (durl != null && durl.size() > 0) {
                        val streamUrl = durl.get(0).asJsonObject.get("url").asString
                        Log.d(TAG, "B站流URL获取成功: ${streamUrl.take(100)}...")
                        streamUrl
                    } else {
                        Log.w(TAG, "B站流URL: durl为空或无数据")
                        null
                    }
                } else {
                    val message = json.asJsonObject.get("message")?.asString ?: "unknown"
                    Log.e(TAG, "B站流URL失败: code=$code, message=$message")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "B站流URL异常", e)
            null
        }
    }

    override suspend fun getRoomTitle(roomId: String, cookie: String?): String {
        return try {
            val builder = Request.Builder()
                .url("https://api.live.bilibili.com/room/v1/Room/get_info?room_id=$roomId")
                .header("User-Agent", USER_AGENT)
                .get()
            cookie?.let { builder.header("Cookie", it) }
            val request = builder.build()

            okHttpClient.newCall(request).execute().use { response ->
                val json = JsonParser.parseString(response.body?.string())
                val code = json.asJsonObject.get("code").asInt
                if (code == 0) {
                    val uid = json.asJsonObject.getAsJsonObject("data")
                        .get("uid").asLong
                    getStreamerName(uid, cookie)
                } else ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private suspend fun getStreamerName(uid: Long, cookie: String?): String {
        return try {
            val builder = Request.Builder()
                .url("https://api.bilibili.com/x/space/acc/info?mid=$uid")
                .header("User-Agent", USER_AGENT)
                .get()
            cookie?.let { builder.header("Cookie", it) }
            val request = builder.build()

            okHttpClient.newCall(request).execute().use { response ->
                val json = JsonParser.parseString(response.body?.string())
                val code = json.asJsonObject.get("code").asInt
                if (code == 0) {
                    json.asJsonObject.getAsJsonObject("data")
                        .get("name").asString
                } else ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private suspend fun getRealRoomId(roomId: String): String {
        return try {
            val request = Request.Builder()
                .url("https://api.live.bilibili.com/room/v1/Room/room_init?id=$roomId")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val json = JsonParser.parseString(response.body?.string())
                val code = json.asJsonObject.get("code").asInt
                if (code == 0) {
                    json.asJsonObject.getAsJsonObject("data")
                        .get("room_id").asLong.toString()
                } else roomId
            }
        } catch (e: Exception) {
            roomId
        }
    }

    override suspend fun getLoginQRCode(): QRCodeData? {
        return try {
            val request = Request.Builder()
                .url("https://passport.bilibili.com/x/passport-login/web/qrcode/generate")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                Log.d(TAG, "B站二维码生成响应: $bodyStr")
                val json = JsonParser.parseString(bodyStr)
                val code = json.asJsonObject.get("code").asInt
                if (code == 0) {
                    val data = json.asJsonObject.getAsJsonObject("data")
                    val url = data.get("url").asString
                    val key = data.get("qrcode_key").asString
                    QRCodeData(url = url, key = key)
                } else {
                    Log.e(TAG, "B站二维码生成失败: code=$code")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "B站二维码生成异常", e)
            null
        }
    }

    override suspend fun checkQRCodeStatus(key: String): LoginStatus {
        return try {
            val request = Request.Builder()
                .url("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$key")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                Log.d(TAG, "B站二维码状态响应: $bodyStr")
                val json = JsonParser.parseString(bodyStr)
                val data = json.asJsonObject.getAsJsonObject("data")
                val statusCode = data.get("code").asInt

                when (statusCode) {
                    86101 -> LoginStatus.Waiting
                    86090 -> LoginStatus.Scanned()
                    86038 -> {
                        // 已确认，从 Cookie header 中提取
                        val cookies = response.headers("Set-Cookie")
                        val cookieStr = cookies.joinToString("; ") { it.substringBefore(";") }
                        Log.d(TAG, "B站登录成功，Cookie: ${cookieStr.take(100)}...")
                        LoginStatus.Confirmed(cookieStr)
                    }
                    86035 -> LoginStatus.Expired
                    else -> LoginStatus.Error("未知状态码: $statusCode")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "B站二维码状态查询异常", e)
            LoginStatus.Error(e.message ?: "查询异常")
        }
    }

    companion object {
        private const val TAG = "BilibiliAdapter"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
