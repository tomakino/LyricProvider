/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import android.content.SharedPreferences
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.system.measureTimeMillis

/**
 * Locates and observes Cloud Music's translation/romanization preference.
 *
 * DexKit is capability detection here: no match, an ambiguous match, or any reflection failure
 * disables both optional lyric modes instead of affecting playback in the host process.
 */
internal class PreferencesMonitor(
    kitBridge: DexKitBridge,
    private val callback: PreferenceCallback,
    private val logger: ModuleLogger,
    hostPackageName: String
) : AutoCloseable {
    private val lock = Any()
    private val getPreferenceMethodData: MethodData?

    private var getPreferenceMethod: Method? = null
    private var preferences: SharedPreferences? = null

    init {
        var discoveredMethod: MethodData? = null
        val elapsed = measureTimeMillis {
            discoveredMethod = discoverPreferenceMethod(kitBridge, hostPackageName)
        }
        getPreferenceMethodData = discoveredMethod
        logger.debug("Preference capability query completed in ${elapsed}ms")
    }

    private val sharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == TRANSLATION_OPTION_KEY) {
                notifyTranslationType(getTranslationType(sharedPreferences))
            }
        }

    /** Rebinds to the actual Application classloader and immediately publishes current state. */
    fun update(classLoader: ClassLoader) {
        unregisterCurrentPreferences()

        val resolvedMethod = getPreferenceMethodData?.let { methodData ->
            try {
                methodData.getMethodInstance(classLoader).apply {
                    isAccessible = true
                }
            } catch (throwable: Throwable) {
                logger.error("Unable to resolve the preference method", throwable)
                null
            }
        }

        synchronized(lock) {
            getPreferenceMethod = resolvedMethod
        }

        val reboundPreferences = lazyGetSharedPreferences()
        notifyTranslationType(getTranslationType(reboundPreferences))
    }

    private fun discoverPreferenceMethod(
        kitBridge: DexKitBridge,
        hostPackageName: String
    ): MethodData? {
        val searchPackages = listOf(
            "$hostPackageName.utils",
            "${Constants.MUSIC_PACKAGE_NAME}.utils"
        ).distinct()
        val preferenceNamespaces = listOf(
            "$hostPackageName.preferences",
            "${Constants.MUSIC_PACKAGE_NAME}.preferences"
        ).distinct()
        val candidates = linkedMapOf<String, MethodData>()

        preferenceNamespaces.forEach { preferenceNamespace ->
            try {
                kitBridge.findClass {
                    searchPackages(searchPackages)
                    matcher {
                        usingStrings(preferenceNamespace, "multiprocess_settings")
                    }
                }.findMethod {
                    matcher {
                        returnType(SharedPreferences::class.java)
                        paramCount = 0
                        modifiers(Modifier.PUBLIC or Modifier.STATIC)
                        usingStrings(preferenceNamespace)
                    }
                }.forEach { methodData ->
                    candidates[methodData.toString()] = methodData
                }
            } catch (throwable: Throwable) {
                logger.error(
                    "DexKit preference query failed for namespace $preferenceNamespace",
                    throwable
                )
            }
        }

        val selected = CapabilitySelection.uniqueOrNull(candidates.values)
        return when (candidates.size) {
            1 -> selected
            0 -> {
                logger.warn("Preference capability unavailable: DexKit found no matching method")
                null
            }

            else -> {
                logger.warn(
                    "Preference capability disabled: DexKit found ${candidates.size} matching methods"
                )
                null
            }
        }
    }

    private fun lazyGetSharedPreferences(): SharedPreferences? = synchronized(lock) {
        preferences?.let { return@synchronized it }
        val method = getPreferenceMethod ?: return@synchronized null

        val resolvedPreferences = try {
            method.invoke(null) as? SharedPreferences
        } catch (throwable: Throwable) {
            logger.error("Invoking the preference method failed", throwable)
            null
        }

        if (resolvedPreferences == null) {
            logger.warn("Preference method returned null or an unexpected value")
            return@synchronized null
        }

        try {
            resolvedPreferences.registerOnSharedPreferenceChangeListener(
                sharedPreferenceChangeListener
            )
            preferences = resolvedPreferences
        } catch (throwable: Throwable) {
            logger.error("Registering the preference listener failed", throwable)
            return@synchronized null
        }
        resolvedPreferences
    }

    fun getTranslationType(
        preference: SharedPreferences? = lazyGetSharedPreferences()
    ): Int = try {
        preference
            ?.getInt(TRANSLATION_OPTION_KEY, DISABLED_TYPE)
            ?.takeIf { it == TRANSLATION_TYPE || it == ROMANIZATION_TYPE }
            ?: DISABLED_TYPE
    } catch (throwable: Throwable) {
        logger.error("Reading the translation preference failed", throwable)
        DISABLED_TYPE
    }

    private fun notifyTranslationType(type: Int) {
        try {
            callback.onTranslationOptionChanged(type)
        } catch (throwable: Throwable) {
            logger.error("Applying the translation preference failed", throwable)
        }
    }

    private fun unregisterCurrentPreferences() {
        val oldPreferences = synchronized(lock) {
            val old = preferences
            preferences = null
            getPreferenceMethod = null
            old
        }
        try {
            oldPreferences?.unregisterOnSharedPreferenceChangeListener(
                sharedPreferenceChangeListener
            )
        } catch (throwable: Throwable) {
            logger.error("Unregistering the old preference listener failed", throwable)
        }
    }

    override fun close() {
        unregisterCurrentPreferences()
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
