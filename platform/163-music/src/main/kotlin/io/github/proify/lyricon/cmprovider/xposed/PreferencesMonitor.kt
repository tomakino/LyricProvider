/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import android.content.SharedPreferences
import com.highcapable.kavaref.extension.classOf
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.cmprovider.xposed.ProviderConfig.NETEASE_MUSIC_PACKAGE_NAME
import io.github.proify.lyricon.dexkit.DexKitSession
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Modifier
import kotlin.system.measureTimeMillis

/** 定位并监听宿主的歌词显示偏好。 */
internal class PreferencesMonitor(
    dexkit: DexKitSession,
    private val hostPackageName: String,
    private val callback: (Int) -> Unit,
) {

    private val preferenceMethod: DexMethod?
    private var preferences: SharedPreferences? = null
    private var boundClassLoader: ClassLoader? = null

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == PREFERENCE_KEY) {
                callback(getTranslationType(sharedPreferences))
            }
        }

    init {
        var method: DexMethod? = null
        val elapsed = measureTimeMillis {
            method = findPreferenceMethod(dexkit)
        }
        preferenceMethod = method
        YLog.debug("Preference capability query completed in ${elapsed}ms")
    }

    /** 绑定宿主 ClassLoader 并回调当前状态。 */
    fun update(classLoader: ClassLoader) {
        if (boundClassLoader != classLoader || preferences == null) {
            bind(classLoader)
        }
        callback(getTranslationType(preferences))
    }

    private fun bind(classLoader: ClassLoader) {
        preferences?.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        preferences = null
        boundClassLoader = classLoader

        val method = preferenceMethod?.getMethodInstance(classLoader) ?: return
        val resolved = runCatching { method.invoke(null) as? SharedPreferences }
            .onFailure { YLog.error("Invoking the preference method failed", it) }
            .getOrNull() ?: return

        resolved.registerOnSharedPreferenceChangeListener(preferenceListener)
        preferences = resolved
    }

    private fun findPreferenceMethod(dexkit: DexKitSession): DexMethod? {
        val searchPackages = listOf(
            "$hostPackageName.utils",
            "${NETEASE_MUSIC_PACKAGE_NAME}.utils"
        ).distinct()

        return dexkit.methodDatas {
            searchPackages(searchPackages)
            matcher {
                declaredClass {
                    usingStrings(".preferences", "multiprocess_settings")
                }
                returnType(classOf<SharedPreferences>())
                paramCount = 0
                modifiers(Modifier.PUBLIC or Modifier.STATIC)
                usingStrings(".preferences")
            }
        }.singleOrNull()
    }

    private fun getTranslationType(preference: SharedPreferences?): Int = try {
        preference?.getInt(PREFERENCE_KEY, TranslationMode.DISABLED) ?: TranslationMode.DISABLED
    } catch (e: Throwable) {
        YLog.error("Reading the translation preference failed", e)
        TranslationMode.DISABLED
    }

    private companion object {
        const val PREFERENCE_KEY = "showLyricSetting"
    }
}
