/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.bodianprovider.xposed

import com.highcapable.yukihookapi.hook.log.YLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

/**
 * Dart → 原生 的歌词投递拦截。
 *
 * 波点是 Flutter 应用，歌词下载在 Dart 侧完成，之后通过 MethodChannel 把
 * 一个 Map 交给原生的 LyricsMgr4FlutterImpl：
 *
 * ```
 * { "rid": "12345678", "isMv": false, "lyricStatus": 1, "lyricPath": "/data/.../cache/lyric/12345678.lrcx" }
 * ```
 *
 * 原生侧承接这个 Map 的类名（`com.tme.push.d3.a$a` 之流）是 R8 重命名的产物，
 * 每次发版都可能变；而 `io.flutter.plugin.common.MethodCall` 属于 Flutter 引擎，
 * 从不参与宿主混淆。所以这里 hook 的是 MethodCall 的构造器——
 * 所有 Dart→原生 的调用都会经过它，我们只按 Map 的键名认领歌词那一条。
 *
 * 代价是这是个热路径，因此判定必须极轻：一次 `is Map` + 一次 `containsKey`。
 */
object FlutterLyricBridge {

    private const val TAG = "BodianFlutter"

    private const val CLASS_METHOD_CALL = "io.flutter.plugin.common.MethodCall"

    const val KEY_RID = "rid"
    const val KEY_LYRIC_PATH = "lyricPath"
    const val KEY_LYRIC_STATUS = "lyricStatus"
    const val KEY_IS_MV = "isMv"

    fun interface Listener {
        fun onLyricDelivered(rid: String?, lyricPath: String, lyricStatus: Int?, isMv: Boolean)
    }

    fun install(classLoader: ClassLoader?, listener: Listener) {
        if (classLoader == null) return
        runCatching {
            XposedHelpers.findAndHookConstructor(
                CLASS_METHOD_CALL,
                classLoader,
                String::class.java,
                Any::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val arguments = param.args.getOrNull(1) as? Map<*, *> ?: return
                        if (!arguments.containsKey(KEY_LYRIC_PATH)) return

                        val path = arguments[KEY_LYRIC_PATH] as? String
                        if (path.isNullOrBlank()) return

                        val rid = arguments[KEY_RID] as? String
                        val status = (arguments[KEY_LYRIC_STATUS] as? Number)?.toInt()
                        val isMv = arguments[KEY_IS_MV] as? Boolean ?: false

                        YLog.debug(
                            tag = TAG,
                            msg = "lyric delivered: method=${param.args.getOrNull(0)}, " +
                                    "rid=$rid, status=$status, isMv=$isMv, path=$path"
                        )

                        runCatching { listener.onLyricDelivered(rid, path, status, isMv) }
                            .onFailure { YLog.error(tag = TAG, msg = "listener failed", e = it) }
                    }
                }
            )
            YLog.debug(tag = TAG, msg = "MethodCall hook installed")
        }.onFailure {
            YLog.error(tag = TAG, msg = "MethodCall hook failed", e = it)
        }
    }
}
