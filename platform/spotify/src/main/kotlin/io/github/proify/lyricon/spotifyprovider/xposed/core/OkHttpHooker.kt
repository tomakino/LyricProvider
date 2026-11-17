/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed.core

import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.extensions.toPairMap
import io.github.proify.lyricon.spotifyprovider.xposed.api.SpotifyApi
import org.luckypray.dexkit.DexKitBridge
import java.util.Locale

/**
 * OkHttp Hook 管理器
 */
class OkHttpHooker(
    private val hooker: YukiBaseHooker,
    private val dexKitManager: DexKitManager
) {

    companion object {
        private const val TAG = "OkHttpHooker"
        private const val CACHE_KEY = "okhttp_headers_class"
    }

    /**
     * Hook OkHttp Headers 类
     */
    fun hook(
        context: Context,
        appClassLoader: ClassLoader,
        versionCode: Long,
        bridge: DexKitBridge
    ) {
        YLog.debug(tag = TAG, msg = "开始 Hook OkHttp Headers")

        val classData = dexKitManager.findClass(
            context = context,
            cacheKey = CACHE_KEY,
            versionCode = versionCode,
            matcher = { findHeadersClass(bridge) }
        )

        if (classData == null) {
            YLog.error(tag = TAG, msg = "未找到 OkHttp Headers 类")
            return
        }

        hookHeadersConstructor(classData, appClassLoader)
        YLog.debug(tag = TAG, msg = "OkHttp Headers Hook 完成")
    }

    private fun findHeadersClass(bridge: DexKitBridge): org.luckypray.dexkit.result.ClassData? {
        return bridge.findClass {
            matcher {
                // okhttp3.Headers (ot10) 多特征联合匹配:
                // 1. toString() 中使用 "██" 遮蔽敏感 header
                // 2. String[] 字段存储 header 键值对
                // 3. 构造器接受 String[]
                // 4. 实现 Iterable 接口
                usingStrings("██")
                fields {
                    add {
                        type = "java.lang.String[]"
                    }
                }
                methods {
                    add {
                        name = "<init>"
                        paramTypes("java.lang.String[]")
                    }
                }
                interfaces {
                    add("java.lang.Iterable")
                }
            }
        }.singleOrNull()
    }

    private fun hookHeadersConstructor(
        classData: org.luckypray.dexkit.result.ClassData,
        appClassLoader: ClassLoader
    ) {
        val headersClass = classData.getInstance(appClassLoader)
        hooker.apply {
            headersClass.resolve().firstConstructor().hook {
                after {
                    val arg = args[0] as? Array<*> ?: return@after
                    arg.toPairMap().forEach { (key, value) ->
                        val keyLowercase = key.lowercase(Locale.ENGLISH)
                        if (keyLowercase in SpotifyApi.keysRequired) {
                            SpotifyApi.recordHeader(keyLowercase, value)
                        }
                    }
                }
            }
        }
    }
}