/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.luckypray.dexkit.DexKitBridge

class LyricInterceptor(
    private val dexKitBridge: DexKitBridge,
    private var classLoader: ClassLoader
) {
    private val TAG = "LyricInterceptor"

    var onLyricIntercepted: ((lyric: String, trans: String?, roma: String?) -> Unit)? = null

    /** 已知歌词相关类：module.lyric 混淆类 (v9.3.35) + UI 视图类 */
    private val knownClasses = listOf(
        "com.netease.cloudmusic.module.lyric.e",
        "com.netease.cloudmusic.module.lyric.f",
        "com.netease.cloudmusic.module.lyric.g",
        "com.netease.cloudmusic.module.lyric.b",
        "com.netease.cloudmusic.module.lyric.l",
        "com.netease.cloudmusic.module.lyric.w",
        "com.netease.cloudmusic.module.lyric.x",
        "com.netease.cloudmusic.ui.LyricView",
        "com.netease.cloudmusic.ui.TwoLineLyricView",
        "com.netease.cloudmusic.ui.LyricViewContainer",
        "com.netease.cloudmusic.ui.LyricLineView",
    )

    /** LRC 行首时间标签正则，兼容 [mm:ss.xx] [mm:ss.xxx] [mm:ss] [mm:ss:xx] 等 */
    private val lrcLinePattern = Regex("""\[\d{1,3}[ :.]\d{2}(?:[ :.]\d{1,3})?].{1,}""")

    private val hookedMethods = mutableSetOf<String>()
    private var nonLrcCallCount = 0

    fun hook() {
        var hookedClassCount = 0
        var totalMethods = 0

        // 阶段 1: Hook 已知类
        for (className in knownClasses) {
            val count = tryHookClass(className)
            if (count > 0) { hookedClassCount++; totalMethods += count }
        }
        Log.i(TAG, "Phase 1: $hookedClassCount/${knownClasses.size} known classes, $totalMethods methods hooked")

        // 阶段 2: DexKit 动态发现
        try {
            val discovered = dexKitBridge.findClass {
                searchPackages("com.netease.cloudmusic.module.lyric")
            }.toList()
            Log.i(TAG, "Phase 2: DexKit found ${discovered.size} extra classes in module.lyric")
            for (cls in discovered) {
                if (cls.name !in knownClasses) {
                    val count = tryHookClass(cls.name)
                    if (count > 0) { hookedClassCount++; totalMethods += count }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "DexKit discovery failed: ${e.message}")
        }

        Log.i(TAG, "TOTAL: $totalMethods methods hooked across $hookedClassCount classes")
    }

    fun rehook(classLoader: ClassLoader) {
        this.classLoader = classLoader
        hookedMethods.clear()
        try {
            dexKitBridge.findClass {
                searchPackages("com.netease.cloudmusic.module.lyric")
            }.forEach { cls ->
                tryHookClass(cls.name)
            }
        } catch (_: Exception) { }
    }

    private fun tryHookClass(className: String): Int {
        var count = 0
        val clazz: Class<*>
        try {
            clazz = XposedHelpers.findClass(className, classLoader)
        } catch (_: Exception) {
            return 0
        }

        Log.i(TAG, "  Class found: $className (${clazz.declaredMethods.size} declared methods)")

        // Hook ALL methods that have at least one String parameter
        // Runtime filtering via isLrcContent will identify actual lyric calls
        for (method in clazz.declaredMethods) {
            val hasStringParam = method.parameterTypes.any { it == String::class.java }
            if (!hasStringParam) continue

            val key = "$className.${method.name}"
            if (key in hookedMethods) continue

            try {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        handleArgs(param.args, "$className.${method.name}")
                    }
                })
                hookedMethods.add(key)
                count++
            } catch (e: Exception) {
                Log.d(TAG, "  Hook FAILED: $key - ${e.message}")
            }
        }
        return count
    }

    private fun handleArgs(args: Array<Any?>, source: String) {
        val strings = args.filterIsInstance<String>()
        if (strings.isEmpty()) return

        val lyricIdx = strings.indexOfFirst { isLrcContent(it) }
        if (lyricIdx == -1) {
            nonLrcCallCount++
            if (nonLrcCallCount <= 5) {
                Log.d(TAG, "Non-LRC #$nonLrcCallCount [$source]: ${strings.first().take(80)}")
            }
            return
        }

        val lyric = strings[lyricIdx]
        val trans = strings.getOrNull(lyricIdx + 1)?.takeIf { isLrcContent(it) }
        val roma = strings.getOrNull(lyricIdx + 2)?.takeIf { isLrcContent(it) }

        Log.i(TAG, "*** LRC HIT! source=$source, lyricLen=${lyric.length}, hasTrans=${trans != null}")
        Log.i(TAG, "*** LRC preview: ${lyric.take(150)}")

        if (lyric.length > 20) {
            onLyricIntercepted?.invoke(lyric, trans, roma)
        }
    }

    private fun isLrcContent(text: String): Boolean {
        val trimmed = text.trimStart()
        return trimmed.startsWith("[") && lrcLinePattern.containsMatchIn(trimmed)
    }
}
