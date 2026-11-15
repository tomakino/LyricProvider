/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed.core

import android.content.Context
import androidx.core.content.edit
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.spotifyprovider.xposed.util.LogUtils
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.ClassData
import java.util.concurrent.ConcurrentHashMap

/**
 * DexKit 管理器，负责 DexKitBridge 生命周期和类查找缓存
 */
class DexKitManager(private val sourceDir: String) {

    companion object {
        private const val TAG = "DexKitManager"
        private const val PREFS_NAME = "dexkit_cache"
    }

    /** 内存缓存 */
    private val memoryCache = ConcurrentHashMap<String, ClassData>()

    /** DexKitBridge 实例 */
    private var bridge: DexKitBridge? = null

    /** 是否已初始化 */
    private var initialized = false

    /**
     * 初始化 DexKitBridge
     */
    fun init() {
        if (initialized) return

        LogUtils.measureAndLog(TAG, "DexKitBridge 初始化") {
            bridge = DexKitBridge.create(sourceDir)
            initialized = true
        }
    }

    /**
     * 查找类，支持多级缓存
     */
    fun findClass(
        context: Context,
        cacheKey: String,
        versionCode: Long,
        matcher: () -> ClassData?
    ): ClassData? {
        // 1. 内存缓存
        memoryCache[cacheKey]?.let {
            YLog.debug(tag = TAG, msg = "内存缓存命中: $cacheKey -> ${it.name}")
            return it
        }

        // 2. 磁盘缓存
        loadFromDisk(context, cacheKey, versionCode)?.let { className ->
            try {
                val classData = bridge?.findClass {
                    matcher { className(className) }
                }?.singleOrNull()
                if (classData != null) {
                    memoryCache[cacheKey] = classData
                    YLog.debug(tag = TAG, msg = "磁盘缓存命中: $cacheKey -> $className")
                    return classData
                }
            } catch (_: Exception) {
                // 缓存失效，重新扫描
            }
        }

        // 3. 全量扫描
        return scanAndCache(context, cacheKey, versionCode, matcher)
    }

    private fun scanAndCache(
        context: Context,
        cacheKey: String,
        versionCode: Long,
        matcher: () -> ClassData?
    ): ClassData? {
        val result = LogUtils.measureAndLog(TAG, "全量扫描 $cacheKey") {
            matcher()
        }

        result?.let {
            memoryCache[cacheKey] = it
            saveToDisk(context, cacheKey, versionCode, it.name)
            YLog.debug(tag = TAG, msg = "全量扫描完成: $cacheKey -> ${it.name}")
        }

        return result
    }

    private fun loadFromDisk(context: Context, cacheKey: String, versionCode: Long): String? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString("${cacheKey}_$versionCode", null)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveToDisk(
        context: Context,
        cacheKey: String,
        versionCode: Long,
        className: String
    ) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit { putString("${cacheKey}_$versionCode", className) }
        } catch (e: Exception) {
            YLog.warn(tag = TAG, msg = "写入磁盘缓存失败", e = e)
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        bridge?.close()
        bridge = null
        initialized = false
        memoryCache.clear()
        YLog.debug(tag = TAG, msg = "资源已释放")
    }
}