/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.spotifyprovider.xposed

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.extensions.toPairMap
import io.github.proify.lyricon.dexkit.DexKitSession
import io.github.proify.lyricon.spotifyprovider.xposed.api.SpotifyApi
import java.util.Locale

/**
 * OkHttp Hook 管理器
 */
class OkHttpHooker(
    private val hooker: YukiBaseHooker,
    private val dexkit: DexKitSession,
) {

    companion object {
        private const val TAG = "OkHttpHooker"
    }

    fun hook() {
        val headersClass = dexkit.classOf {
            matcher {
                // okhttp3.Headers (ot10) 多特征联合匹配:
                // 1. toString() 中使用 "██" 遮蔽敏感 header
                // 2. String[] 字段存储 header 键值对
                // 3. 构造器接受 String[]
                // 4. 实现 Iterable 接口
                usingStrings("██")
                fields { addForType("java.lang.String[]") }
                methods {
                    add {
                        name = "<init>"
                        paramTypes("java.lang.String[]")
                    }
                }
                interfaces { add("java.lang.Iterable") }
            }
        }

        if (headersClass == null) {
            YLog.error(tag = TAG, msg = "未找到 OkHttp Headers 类")
            return
        }

        hookHeadersConstructor(headersClass)
        YLog.debug(tag = TAG, msg = "OkHttp Headers Hook 完成")
    }

    private fun hookHeadersConstructor(headersClass: Class<*>) {
        hooker.apply {
            headersClass.resolve()
                .firstConstructor().hook {
                    after {
                        val arg = args[0] as? Array<*> ?: return@after
                        arg.toPairMap().forEach { (key, value) ->
                            val keyLowercase = key.lowercase(Locale.ENGLISH)
                            if (keyLowercase in SpotifyApi.REQUIRED_HEADERS) {
                                SpotifyApi.capturedHeaders[keyLowercase] = value
                            }
                        }
                    }
                }
        }
    }
}