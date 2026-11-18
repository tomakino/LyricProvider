/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import android.content.SharedPreferences
import com.highcapable.kavaref.extension.classOf
import com.highcapable.yukihookapi.hook.log.YLog
import io.github.proify.lyricon.cmprovider.xposed.Constants.NETEASE_MUSIC_PACKAGE_NAME
import io.github.proify.lyricon.dexkit.DexKitSession
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Modifier
import kotlin.system.measureTimeMillis

class PreferencesMonitor(
    private val dexkit: DexKitSession,
    private val hostPackageName: String,
    private val callback: PreferenceCallback,
) {

    private val getPreferenceDexMethod: DexMethod?
    private var preferences: SharedPreferences? = null

    private var classLoader: ClassLoader? = null
    private var lastClassLoader: ClassLoader? = null

    private val sharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == TRANSLATION_OPTION_KEY) {
                notifyTranslationTypeChanged(getTranslationType(sharedPreferences))
            }
        }

    init {
        var dexMethod: DexMethod?
        val elapsed = measureTimeMillis {
            dexMethod = findGetPreferenceMethod(dexkit, hostPackageName)
        }
        YLog.debug("Preference capability query completed in ${elapsed}ms")

        getPreferenceDexMethod = dexMethod
    }

    fun update(newClassLoader: ClassLoader) {
        lastClassLoader = classLoader
        classLoader = newClassLoader

        val preferences = getSharedPreferences()
        notifyTranslationTypeChanged(getTranslationType(preferences))
    }

    private fun findGetPreferenceMethod(
        dexkit: DexKitSession,
        hostPackageName: String
    ): DexMethod? {

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

    private fun getSharedPreferences(): SharedPreferences? {
        val classLoader = this.classLoader

        if (classLoader != null && lastClassLoader != classLoader) {
            preferences?.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
            preferences = null

            val dexMethod: DexMethod? = getPreferenceDexMethod
            val method = dexMethod?.getMethodInstance(classLoader)

            val newPreferences = try {
                method?.invoke(null) as? SharedPreferences
            } catch (e: Throwable) {
                YLog.error("Invoking the preference method failed", e)
                null
            }

            newPreferences?.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
            preferences = newPreferences
        }

        return preferences
    }

    private fun getTranslationType(
        preference: SharedPreferences? = getSharedPreferences()
    ): Int = try {
        preference
            ?.getInt(TRANSLATION_OPTION_KEY, DISABLED_TYPE)
            ?: DISABLED_TYPE
    } catch (e: Throwable) {
        YLog.error("Reading the translation preference failed", e)
        DISABLED_TYPE
    }

    private fun notifyTranslationTypeChanged(type: Int) {
        callback.onTranslationOptionChanged(type)
    }

    fun interface PreferenceCallback {
        fun onTranslationOptionChanged(type: Int)
    }

    private companion object {
        const val TRANSLATION_OPTION_KEY = "showLyricSetting"
        const val DISABLED_TYPE = -1
        const val TRANSLATION_TYPE = 0
        const val ROMANIZATION_TYPE = 1
    }
}