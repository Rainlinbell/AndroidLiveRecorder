package com.liverecorder.app.recorder

import com.liverecorder.app.platform.PlatformAdapter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一管理各平台的直播流地址提取
 */
@Singleton
class StreamExtractor @Inject constructor(
    private val adapters: Set<@JvmSuppressWildcards PlatformAdapter>
) {
    /**
     * 根据URL找到对应的平台适配器
     */
    fun findAdapter(url: String): PlatformAdapter? {
        return adapters.firstOrNull { it.matchUrl(url) }
    }

    /**
     * 根据平台ID找到对应的适配器
     */
    fun findAdapterById(platformId: String): PlatformAdapter? {
        return adapters.firstOrNull { it.platformId == platformId }
    }

    /**
     * 获取所有支持的平台适配器
     */
    fun getAllAdapters(): Set<PlatformAdapter> = adapters
}
