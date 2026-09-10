package com.liverecorder.app.platform

/**
 * 二维码登录数据
 */
data class QRCodeData(
    /** 二维码包含的 URL 内容 */
    val url: String,
    /** 用于轮询状态的唯一标识 */
    val key: String,
    /** 二维码图片 URL（部分平台直接返回图片 URL，可为 null） */
    val imageUrl: String? = null
)

/**
 * 二维码登录状态
 */
sealed class LoginStatus {
    /** 等待扫码 */
    data object Waiting : LoginStatus()

    /** 已扫码待确认 */
    data class Scanned(val userName: String = "") : LoginStatus()

    /** 已确认登录，返回 Cookie */
    data class Confirmed(val cookie: String) : LoginStatus()

    /** 二维码已过期 */
    data object Expired : LoginStatus()

    /** 查询出错 */
    data class Error(val message: String) : LoginStatus()
}
