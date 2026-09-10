package com.liverecorder.app.di

import android.content.Context
import com.liverecorder.app.data.db.AppDatabase
import com.liverecorder.app.data.db.LiveRoomDao
import com.liverecorder.app.platform.*
import com.liverecorder.app.root.RootManager
import com.liverecorder.app.storage.OutputStorageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // 无读取超时，支持长时间直播流
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideLiveRoomDao(database: AppDatabase): LiveRoomDao {
        return database.liveRoomDao()
    }

    @Provides
    @Singleton
    fun providePlatformAdapters(
        bilibiliAdapter: BilibiliAdapter,
        douyinAdapter: DouyinAdapter,
        douyuAdapter: DouyuAdapter,
        huyaAdapter: HuyaAdapter
    ): Set<PlatformAdapter> {
        return setOf(bilibiliAdapter, douyinAdapter, douyuAdapter, huyaAdapter)
    }

    @Provides
    @Singleton
    fun provideRootManager(): RootManager {
        return RootManager()
    }

    @Provides
    @Singleton
    fun provideOutputStorageManager(@ApplicationContext context: Context): OutputStorageManager {
        return OutputStorageManager(context)
    }
}
