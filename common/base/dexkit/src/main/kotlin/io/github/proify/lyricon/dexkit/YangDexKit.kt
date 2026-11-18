/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.dexkit

import android.content.pm.ApplicationInfo
import org.luckypray.dexkit.DexKitCacheBridge
import org.luckypray.dexkit.annotations.DexKitExperimentalApi
import java.io.File

/** 本项目对 DexKit 的封装入口：缓存初始化与命名空间管理。 */
@OptIn(DexKitExperimentalApi::class)
object YangDexKit {

    init {
        System.loadLibrary("dexkit")
    }

    private const val CACHE_DIR = ".lyricon-dexkit-cache"
    private const val IDLE_TIMEOUT_MILLIS = 5_000L

    private var fileCache: DexKitFileCache? = null

    /** 绑定宿主上下文；同一进程多次调用会复用缓存并清掉旧命名空间。 */
    fun session(appInfo: ApplicationInfo, classLoader: ClassLoader): DexKitSession {
        val cache = ensureInitialized(
            File(appInfo.dataDir, "cache").absolutePath
        )
        val apk = File(appInfo.sourceDir)
        val appTag = "${appInfo.packageName}:${apk.length()}-${apk.lastModified()}"

        cache.bind(appTag)
        cache.retainOnly(appTag)
        return DexKitSession(appTag, appInfo.sourceDir, classLoader)
    }

    @Synchronized
    private fun ensureInitialized(dataDir: String): DexKitFileCache {
        fileCache?.let { return it }

        val cache = DexKitFileCache(File(dataDir, CACHE_DIR))
        DexKitCacheBridge.idleTimeoutMillis = IDLE_TIMEOUT_MILLIS
        DexKitCacheBridge.cachePolicy = DexKitCacheBridge.CachePolicy(
            cacheSuccess = true,
            failurePolicy = DexKitCacheBridge.CacheFailurePolicy.NONE,
        )
        DexKitCacheBridge.init(cache)
        fileCache = cache
        return cache
    }
}
