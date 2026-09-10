package com.liverecorder.app.platform

import android.util.Log
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DouyinAdapter @Inject constructor(
    private val okHttpClient: OkHttpClient
) : PlatformAdapter {

    override val platformName = "抖音"
    override val platformId = "douyin"
    override val loginUrl = "https://www.douyin.com/login"

    override fun matchUrl(url: String): Boolean {
        return url.contains("live.douyin.com")
    }

    override suspend fun getRoomId(url: String): String {
        val regex = Regex("""live\.douyin\.com/(\d+)""")
        return regex.find(url)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("无法从URL中提取抖音房间号")
    }

    override suspend fun isLive(roomId: String, cookie: String?): Boolean {
        return try {
            val actualCookie = cookie ?: getTtwidCookie()
            val builder = Request.Builder()
                .url("https://live.douyin.com/webcast/room/web/enter/?aid=6383&live_id=1&device_platform=web&language=zh-CN&enter_from=web_live&cookie_enabled=true&screen_width=1920&screen_height=1080&browser_language=zh-CN&browser_platform=Win32&browser_name=Chrome&browser_version=120.0.0.0&web_rid=$roomId")
                .header("User-Agent", USER_AGENT)
                .get()
            actualCookie?.let { builder.header("Cookie", it) }
            val request = builder.build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                Log.d(TAG, "抖音直播状态响应: code=${response.code}, bodyLen=${bodyStr?.length ?: 0}")
                if (bodyStr.isNullOrEmpty()) {
                    Log.w(TAG, "抖音直播状态: 响应体为空")
                    return@use false
                }
                val json = JsonParser.parseString(bodyStr)
                if (json.isJsonNull || !json.isJsonObject) {
                    Log.w(TAG, "抖音直播状态: 响应非JSON对象")
                    return@use false
                }
                val jsonObj = json.asJsonObject
                val statusCode = jsonObj.get("status_code")?.asInt ?: return@use false
                if (statusCode == 0) {
                    val data = jsonObj.getAsJsonObject("data")
                    val dataArray = data?.getAsJsonArray("data")
                    if (dataArray != null && dataArray.size() > 0) {
                        val roomInfo = dataArray.get(0).asJsonObject
                        val status = roomInfo.get("status")?.asInt ?: 0
                        Log.d(TAG, "抖音直播状态: room=$roomId, status=$status")
                        status == 2
                    } else {
                        Log.w(TAG, "抖音直播状态: data数组为空")
                        false
                    }
                } else {
                    Log.w(TAG, "抖音直播状态查询失败: status_code=$statusCode")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "抖音直播状态异常", e)
            false
        }
    }

    override suspend fun getStreamUrl(roomId: String, quality: String, cookie: String?): String? {
        return try {
            val actualCookie = cookie ?: getTtwidCookie()
            val builder = Request.Builder()
                .url("https://live.douyin.com/webcast/room/web/enter/?aid=6383&live_id=1&device_platform=web&language=zh-CN&enter_from=web_live&cookie_enabled=true&screen_width=1920&screen_height=1080&browser_language=zh-CN&browser_platform=Win32&browser_name=Chrome&browser_version=120.0.0.0&web_rid=$roomId")
                .header("User-Agent", USER_AGENT)
                .get()
            actualCookie?.let { builder.header("Cookie", it) }
            val request = builder.build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                Log.d(TAG, "抖音流URL响应: code=${response.code}, bodyLen=${bodyStr?.length ?: 0}")
                if (bodyStr.isNullOrEmpty()) {
                    Log.w(TAG, "抖音流URL: 响应体为空")
                    return@use null
                }
                val json = JsonParser.parseString(bodyStr)
                if (json.isJsonNull || !json.isJsonObject) {
                    Log.w(TAG, "抖音流URL: 响应非JSON对象")
                    return@use null
                }
                val jsonObj = json.asJsonObject
                val statusCode = jsonObj.get("status_code")?.asInt ?: return@use null
                if (statusCode == 0) {
                    val data = jsonObj.getAsJsonObject("data")
                    val dataArray = data?.getAsJsonArray("data")
                    if (dataArray == null || dataArray.size() == 0) {
                        Log.w(TAG, "抖音流URL: data数组为空")
                        return@use null
                    }
                    val roomInfo = dataArray.get(0).asJsonObject
                    val streamUrlObj = roomInfo.getAsJsonObject("stream_url")
                    if (streamUrlObj == null) {
                        Log.w(TAG, "抖音流URL: stream_url为空")
                        return@use null
                    }
                    val flvPullUrl = streamUrlObj.getAsJsonObject("flv_pull_url")
                    if (flvPullUrl == null) {
                        Log.w(TAG, "抖音流URL: flv_pull_url为空")
                        return@use null
                    }
                    Log.d(TAG, "抖音流URL画质选项: $flvPullUrl")
                    // 根据画质选择对应的流
                    val url = when (quality) {
                        "low" -> flvPullUrl.get("SD1")?.asString
                            ?: flvPullUrl.get("HD1")?.asString
                            ?: flvPullUrl.get("FULL_HD1")?.asString
                        "medium" -> flvPullUrl.get("HD1")?.asString
                            ?: flvPullUrl.get("FULL_HD1")?.asString
                        else -> flvPullUrl.get("FULL_HD1")?.asString
                            ?: flvPullUrl.get("HD1")?.asString
                            ?: flvPullUrl.get("SD1")?.asString
                    }
                    if (url != null) {
                        Log.d(TAG, "抖音流URL获取成功: ${url.take(100)}...")
                    } else {
                        Log.w(TAG, "抖音流URL: 无匹配画质")
                    }
                    url
                } else {
                    Log.e(TAG, "抖音流URL失败: status_code=$statusCode")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "抖音流URL异常", e)
            null
        }
    }

    override suspend fun getRoomTitle(roomId: String, cookie: String?): String {
        return try {
            val actualCookie = cookie ?: getTtwidCookie()
            val builder = Request.Builder()
                .url("https://live.douyin.com/webcast/room/web/enter/?aid=6383&live_id=1&device_platform=web&language=zh-CN&web_rid=$roomId")
                .header("User-Agent", USER_AGENT)
                .get()
            actualCookie?.let { builder.header("Cookie", it) }
            val request = builder.build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                Log.d(TAG, "抖音房间标题响应: ${bodyStr?.take(500)}")
                val json = JsonParser.parseString(bodyStr)
                val statusCode = json.asJsonObject.get("status_code").asInt
                if (statusCode == 0) {
                    val data = json.asJsonObject.getAsJsonObject("data")
                    val dataArray = data.getAsJsonArray("data")
                    if (dataArray != null && dataArray.size() > 0) {
                        val roomInfo = dataArray.get(0).asJsonObject
                        // 尝试多种可能的主播名称字段
                        val ownerInfo = roomInfo.getAsJsonObject("owner")
                        var nickname: String? = null
                        
                        if (ownerInfo != null) {
                            nickname = ownerInfo.get("nickname")?.asString
                            if (nickname.isNullOrEmpty()) {
                                nickname = ownerInfo.get("nickname")?.asString
                            }
                        }
                        
                        // 如果通过owner没获取到，尝试其他可能的字段
                        if (nickname.isNullOrEmpty()) {
                            nickname = roomInfo.get("anchor_nick_name")?.asString
                        }
                        if (nickname.isNullOrEmpty()) {
                            nickname = roomInfo.get("nickname")?.asString
                        }
                        if (nickname.isNullOrEmpty()) {
                            nickname = roomInfo.get("title")?.asString
                        }
                        
                        Log.d(TAG, "抖音房间信息 - 最终昵称: $nickname, roomId: $roomId")
                        
                        // 确保返回的不是房间ID（纯数字）
                        if (!nickname.isNullOrEmpty() && !nickname.all { it.isDigit() }) {
                            nickname
                        } else {
                            "" // 返回空字符串而不是房间ID
                        }
                    } else {
                        Log.w(TAG, "抖音房间信息: data数组为空")
                        ""
                    }
                } else {
                    Log.w(TAG, "抖音房间标题获取失败: status_code=$statusCode")
                    ""
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取抖音房间标题异常", e)
            ""
        }
    }

    /** 获取抖音 ttwid cookie（访问首页获取） */
    private suspend fun getTtwidCookie(): String? {
        return try {
            val request = Request.Builder()
                .url("https://live.douyin.com/")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                val cookies = response.headers("Set-Cookie")
                val ttwid = cookies.firstOrNull { it.contains("ttwid=") }
                    ?.substringBefore(";")
                if (ttwid != null) {
                    Log.d(TAG, "获取抖音ttwid成功: ${ttwid.take(50)}...")
                    ttwid
                } else {
                    Log.w(TAG, "获取抖音ttwid失败, cookies=$cookies")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取抖音ttwid异常", e)
            null
        }
    }

    override suspend fun getLoginQRCode(): QRCodeData? {
        return try {
            val request = Request.Builder()
                .url("https://sso.douyin.com/passport/web/get_qr_code/?aid=6383&service=https%3A%2F%2Flive.douyin.com&need_browser_verify=0")
                .header("User-Agent", USER_AGENT)
                .post(okhttp3.FormBody.Builder().build())
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                Log.d(TAG, "抖音二维码生成响应: ${bodyStr?.take(500)}")
                if (bodyStr.isNullOrEmpty()) return@use null
                val json = JsonParser.parseString(bodyStr)
                val data = json.asJsonObject.getAsJsonObject("data")
                if (data != null) {
                    val token = data.get("token")?.asString ?: data.get("qrcode_key")?.asString
                    val qrUrl = data.get("qrcode_url")?.asString
                        ?: data.get("verify_qrcode_url")?.asString
                    if (token != null && qrUrl != null) {
                        QRCodeData(url = qrUrl, key = token)
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "抖音二维码生成异常", e)
            null
        }
    }

    override suspend fun checkQRCodeStatus(key: String): LoginStatus {
        return try {
            val request = Request.Builder()
                .url("https://sso.douyin.com/passport/web/check_qr_connect/?aid=6383&service=https%3A%2F%2Flive.douyin.com&token=$key")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                Log.d(TAG, "抖音二维码状态响应: ${bodyStr?.take(500)}")
                if (bodyStr.isNullOrEmpty()) return@use LoginStatus.Error("响应为空")
                val json = JsonParser.parseString(bodyStr)
                val data = json.asJsonObject.getAsJsonObject("data")
                val status = data?.get("status")?.asString
                    ?: json.asJsonObject.get("message")?.asString

                when (status) {
                    "1", "new" -> LoginStatus.Waiting
                    "2", "scan" -> LoginStatus.Scanned()
                    "3", "confirmed" -> {
                        val cookies = response.headers("Set-Cookie")
                        val cookieStr = cookies.joinToString("; ") { it.substringBefore(";") }
                        LoginStatus.Confirmed(cookieStr)
                    }
                    "5", "expired" -> LoginStatus.Expired
                    else -> LoginStatus.Error("未知状态: $status")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "抖音二维码状态查询异常", e)
            LoginStatus.Error(e.message ?: "查询异常")
        }
    }

    companion object {
        private const val TAG = "DouyinAdapter"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
