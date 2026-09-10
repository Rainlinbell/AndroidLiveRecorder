package com.liverecorder.app.platform

/**
 * 平台适配器接口，每个直播平台需要实现此接口
 */
interface PlatformAdapter {
    /** 平台名称 */
    val platformName: String

    /** 平台标识 */
    val platformId: String

    /** 该平台登录页面 URL（用于获取 Cookie） */
    val loginUrl: String

    /** 判断URL是否属于该平台 */
    fun matchUrl(url: String): Boolean

    /** 从URL中提取房间号 */
    suspend fun getRoomId(url: String): String

    /** 检测直播间是否正在直播 */
    suspend fun isLive(roomId: String, cookie: String? = null): Boolean

    /** 获取直播流地址，quality: original/high/medium/low */
    suspend fun getStreamUrl(roomId: String, quality: String = "original", cookie: String? = null): String?

    /** 获取直播间标题 */
    suspend fun getRoomTitle(roomId: String, cookie: String? = null): String

    /** 获取登录二维码，不支持时返回 null */
    suspend fun getLoginQRCode(): QRCodeData? = null

    /** 轮询二维码扫描状态 */
    suspend fun checkQRCodeStatus(key: String): LoginStatus = LoginStatus.Error("该平台不支持二维码登录")
}
