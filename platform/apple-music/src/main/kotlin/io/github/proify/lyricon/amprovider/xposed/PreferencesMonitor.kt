/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed

import android.annotation.SuppressLint
import android.content.Context
import com.highcapable.kavaref.extension.classOf
import com.highcapable.kavaref.extension.isStatic
import com.highcapable.kavaref.extension.makeAccessible
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.amprovider.xposed.util.callStaticMethodOrNull
import java.lang.reflect.Method
import java.lang.reflect.Modifier

// todo 待优化各版本混淆适配
/** 翻译开关：定位候选 setter 并派发状态，hook 由 [Apple] 注册。 */
@SuppressLint("StaticFieldLeak")
object PreferencesMonitor {

    private const val TAG = "AMProvider"

    const val TRANSLATION_KEY: String = "key_player_lyrics_translation_selected"

    private const val LEGACY_PREFS_CLASS = "com.apple.android.music.utils.AppSharedPreferences"
    private const val LEGACY_SETTER = "setLyricsTranslationSelected"
    private const val LEGACY_PREFS_FILE = "preferences"

    /** 待 hook 的 setter。 */
    data class HookTarget(val className: String, val setterName: String)

    private val OBFUSCATED_CANDIDATES: List<HookTarget> = listOf(
        HookTarget(className = "ja.i0", setterName = "n")
    )

    private lateinit var context: Context

    var listener: Listener? = null

    @Volatile
    var translationSelected: Boolean? = null
        private set

    @Volatile
    var isHookInstalled: Boolean = false
        private set

    fun initialize(context: Context) {
        if (::context.isInitialized) return
        this.context = context.applicationContext
    }

    /** 老版本类优先；混淆候选须通过偏好键校验。 */
    fun resolveHookTargets(classLoader: ClassLoader): List<HookTarget> {
        val targets = mutableListOf<HookTarget>()

        runCatching {
            val clazz = classLoader.loadClass(LEGACY_PREFS_CLASS)
            if (clazz.declaredMethods.any { it.name == LEGACY_SETTER && it.isBooleanSetter() }) {
                targets += HookTarget(LEGACY_PREFS_CLASS, LEGACY_SETTER)
            }
        }

        for (candidate in OBFUSCATED_CANDIDATES) {
            runCatching {
                val clazz = classLoader.loadClass(candidate.className)
                if (!holdsTranslationKey(clazz)) {
                    YLog.warn(
                        tag = TAG,
                        msg = "PreferencesMonitor: ${candidate.className} 未持有 $TRANSLATION_KEY，跳过"
                    )
                    return@runCatching
                }
                val isSetter = clazz.declaredMethods.any {
                    it.name == candidate.setterName && it.isBooleanSetter()
                }
                if (isSetter) {
                    targets += candidate
                }
            }
        }
        return targets
    }

    fun markHookInstalled() {
        isHookInstalled = true
    }

    /** setter 执行后派发新状态。 */
    fun dispatch(selected: Boolean) {
        translationSelected = selected
        listener?.onTranslationSelectedChanged(selected)
    }

    /** 读取初始值；7.x getter 阻塞，放后台线程。 */
    fun readInitialValue(classLoader: ClassLoader) {
        if (::context.isInitialized) {
            translationSelected = runCatching {
                context.getSharedPreferences(LEGACY_PREFS_FILE, Context.MODE_PRIVATE)
                    .getBoolean(TRANSLATION_KEY, false)
            }.getOrNull()
        }

        Thread({
            val value = readObfuscatedValue(classLoader)
            if (value != null) {
                YLog.debug(tag = TAG, msg = "PreferencesMonitor: 初始翻译开关=$value")
                dispatch(value)
            }
        }, "AMProvider-PrefReader").apply { isDaemon = true }.start()
    }

    fun resolveDisplayTranslation(): Boolean = translationSelected ?: false

    private fun readObfuscatedValue(classLoader: ClassLoader): Boolean? {
        for ((className) in OBFUSCATED_CANDIDATES) {
            val clazz = runCatching {
                classLoader.loadClass(className)
            }.getOrNull() ?: continue
            val getter = clazz.declaredMethods.firstOrNull {
                it.name == "g" && it.isStatic && it.parameterTypes.isEmpty()
            } ?: continue
            val value = clazz.callStaticMethodOrNull(getter.name) as? Boolean
            if (value != null) return value
        }
        return null
    }

    private fun Method.isBooleanSetter(): Boolean = Modifier.isStatic(modifiers) &&
            parameterTypes.size == 1 &&
            parameterTypes[0] == java.lang.Boolean.TYPE

    private fun holdsTranslationKey(clazz: Class<*>): Boolean = clazz.declaredFields.any { field ->
        if (!field.isStatic) return@any false
        if (field.type.isPrimitive || field.type.name.startsWith("java.")) return@any false

        val holder = runCatching {
            field.makeAccessible()
            field.get(null)
        }.getOrNull() ?: return@any false

        holderHoldsTranslationKey(holder)
    }

    private fun holderHoldsTranslationKey(holder: Any): Boolean {
        val holdsKeyField = holder.javaClass.declaredFields.any { field ->
            if (field.type != classOf<String>()) return@any false
            runCatching {
                field.makeAccessible()
                field.get(holder)
            }.getOrNull() == TRANSLATION_KEY
        }
        return holdsKeyField || runCatching { holder.toString() == TRANSLATION_KEY }.getOrDefault(false)
    }

    interface Listener {
        fun onTranslationSelectedChanged(selected: Boolean)
    }
}
