/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed

import android.annotation.SuppressLint
import android.content.Context
import com.highcapable.yukihookapi.hook.log.YLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * 监听 Apple Music 的"翻译歌词"偏好设置变化。
 *
 * - Apple Music 6.x 及以前：该开关由 `com.apple.android.music.utils.AppSharedPreferences`
 *   管理，直接 Hook `setLyricsTranslationSelected(boolean)` 即可。
 * - Apple Music 7.0.0-beta：该设置类已被混淆移除，设置项整体迁移到基于 DataStore 的偏好框架。
 *   翻译开关位于混淆类 `ja.i0`（偏好键 `key_player_lyrics_translation_selected`），
 *   静态方法 `n(boolean)` 为 setter、`g()` 为阻塞式 getter，且默认值为 `true`。
 *
 * 由于目标类名不再稳定，这里采用「候选表 + 运行期校验 + 逐级降级」：
 * 1. 先尝试老版本 Hook，保持对旧版 Apple Music 的兼容；
 * 2. 再尝试 7.x 混淆候选，并且必须校验候选类中确实持有该偏好键才允许 Hook，
 *    避免 Hook 到无关方法；
 * 3. 全部失败时只记录日志，**绝不抛异常**（旧实现会因 ClassNotFoundException 中断整个初始化流程），
 *    展示开关退化为"有翻译就展示"，与 Apple Music 自身默认值一致。
 */
@SuppressLint("StaticFieldLeak")
object PreferencesMonitor {

    private const val TAG = "AMProvider"

    /** Apple Music 偏好中"翻译歌词"开关的键。 */
    const val TRANSLATION_KEY: String = "key_player_lyrics_translation_selected"

    private const val LEGACY_PREFS_CLASS = "com.apple.android.music.utils.AppSharedPreferences"
    private const val LEGACY_SETTER = "setLyricsTranslationSelected"
    private const val LEGACY_PREFS_FILE = "preferences"

    /**
     * Apple Music 7.x 混淆偏好门面候选。
     * `ja.i0` 为 7.0.0-beta 的映射（setter `n` / getter `g`）。
     */
    private val OBFUSCATED_CANDIDATES: List<ObfuscatedCandidate> = listOf(
        ObfuscatedCandidate(className = "ja.i0", setterName = "n", getterName = "g")
    )

    private data class ObfuscatedCandidate(
        val className: String,
        val setterName: String,
        val getterName: String
    )

    private lateinit var context: Context

    var listener: Listener? = null

    /**
     * 当前"翻译歌词"开关状态。
     * `null` 表示尚未确定（7.x 初始值需要异步读取），此时按 Apple Music 默认值处理。
     */
    @Volatile
    var translationSelected: Boolean? = null
        private set

    /** 是否成功接管了开关变化，用于诊断日志。 */
    @Volatile
    var isHookInstalled: Boolean = false
        private set

    fun initialize(context: Context) {
        if (::context.isInitialized) return
        this.context = context.applicationContext

        isHookInstalled = hookLegacy() || hookObfuscated()

        if (!isHookInstalled) {
            YLog.warn(
                tag = TAG,
                msg = "PreferencesMonitor: translation switch hook unavailable, " +
                        "translation display follows lyrics content"
            )
        }
    }

    /**
     * 提供给 Provider 的翻译展示开关。
     * 已知时跟随 Apple Music 设置，未知时返回 `true`（Apple Music 自身默认值）。
     */
    fun resolveDisplayTranslation(): Boolean = translationSelected ?: true

    //region 策略 1：老版本 AppSharedPreferences

    private fun hookLegacy(): Boolean = runCatching {
        val clazz = context.classLoader.loadClass(LEGACY_PREFS_CLASS)
        val setter = clazz.declaredMethods.firstOrNull {
            it.name == LEGACY_SETTER && it.isBooleanSetter()
        } ?: return@runCatching false

        XposedBridge.hookMethod(setter, booleanHook())
        translationSelected = runCatching {
            context.getSharedPreferences(LEGACY_PREFS_FILE, Context.MODE_PRIVATE)
                .getBoolean(TRANSLATION_KEY, false)
        }.getOrNull()

        YLog.debug("PreferencesMonitor: hooked legacy $LEGACY_PREFS_CLASS#$LEGACY_SETTER")
        true
    }.getOrElse {
        YLog.debug("PreferencesMonitor: legacy hook unavailable (${it.javaClass.simpleName})")
        false
    }

    //endregion

    //region 策略 2：Apple Music 7.x 混淆偏好门面

    private fun hookObfuscated(): Boolean {
        for (candidate in OBFUSCATED_CANDIDATES) {
            val installed = runCatching { installObfuscated(candidate) }.getOrElse { error ->
                YLog.debug("PreferencesMonitor: ${candidate.className} probe failed: ${error.message}")
                false
            }
            if (installed) return true
        }
        return false
    }

    private fun installObfuscated(candidate: ObfuscatedCandidate): Boolean {
        val clazz = context.classLoader.loadClass(candidate.className)

        // 运行期校验：候选类必须真的持有"翻译歌词"偏好键，否则拒绝 Hook。
        if (!holdsTranslationKey(clazz)) {
            YLog.warn(
                tag = TAG,
                msg = "PreferencesMonitor: ${candidate.className} does not hold $TRANSLATION_KEY, skipped"
            )
            return false
        }

        val setter = clazz.declaredMethods.firstOrNull {
            it.name == candidate.setterName && it.isBooleanSetter()
        } ?: return false

        XposedBridge.hookMethod(setter, booleanHook())
        YLog.debug("PreferencesMonitor: hooked ${candidate.className}#${candidate.setterName}")

        // 初始值：混淆门面提供阻塞式 getter，放到后台线程读取，避免阻塞主线程。
        clazz.declaredMethods.firstOrNull {
            it.name == candidate.getterName &&
                    Modifier.isStatic(it.modifiers) &&
                    it.parameterTypes.isEmpty()
        }?.let(::readInitialValueAsync)

        return true
    }

    private fun readInitialValueAsync(getter: Method) {
        Thread({
            val value = runCatching { getter.invoke(null) as? Boolean }.getOrNull()
            if (value != null) {
                YLog.debug("PreferencesMonitor: initial translation selected = $value")
                dispatch(value)
            }
        }, "AMProvider-PrefReader").apply { isDaemon = true }.start()
    }

    /**
     * 校验混淆设置类中是否持有目标偏好键。
     *
     * 这类类把偏好键包装成"只有一个字符串"的对象存放在静态字段里，而该字符串字段的名字同样会被混淆
     * （7.0.0-beta 实测为 `a`，并非源码里的 `name`），因此这里不依赖字段名：
     * 扫描包装对象的全部 String 字段，并以 `toString()` 兜底。
     *
     * 读取静态字段会触发该类的 `<clinit>`，其内容只是构造若干偏好键对象，开销可忽略。
     */
    private fun holdsTranslationKey(clazz: Class<*>): Boolean = clazz.declaredFields.any { field ->
        if (!Modifier.isStatic(field.modifiers)) return@any false
        if (field.type.isPrimitive || field.type.name.startsWith("java.")) return@any false

        val holder = runCatching {
            field.isAccessible = true
            field.get(null)
        }.getOrNull() ?: return@any false

        holderHoldsTranslationKey(holder)
    }

    private fun holderHoldsTranslationKey(holder: Any): Boolean {
        val holdsKeyField = holder.javaClass.declaredFields.any { field ->
            if (field.type != String::class.java) return@any false
            runCatching {
                field.isAccessible = true
                field.get(holder)
            }.getOrNull() == TRANSLATION_KEY
        }
        if (holdsKeyField) return true

        return runCatching { holder.toString() == TRANSLATION_KEY }.getOrDefault(false)
    }

    //endregion

    //region 工具

    private fun Method.isBooleanSetter(): Boolean = Modifier.isStatic(modifiers) &&
            parameterTypes.size == 1 &&
            parameterTypes[0] == java.lang.Boolean.TYPE

    private fun booleanHook(): XC_MethodHook = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            (param.args?.getOrNull(0) as? Boolean)?.let(::dispatch)
        }
    }

    private fun dispatch(selected: Boolean) {
        translationSelected = selected
        listener?.onTranslationSelectedChanged(selected)
    }

    //endregion

    interface Listener {
        fun onTranslationSelectedChanged(selected: Boolean)
    }
}
