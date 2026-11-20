/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.preferences

import android.content.Context

/** 按宿主存储实现选择开关数据源。 */
internal object TranslationSwitchFactory {

    fun create(
        context: Context,
        classLoader: ClassLoader,
        onChanged: (Boolean) -> Unit,
    ): TranslationSwitch =
        if (supportsLegacyPreferences(classLoader)) {
            SharedPreferencesTranslationSwitch(context, onChanged)
        } else {
            DataStoreTranslationSwitch(context.dataDir, onChanged)
        }

    private fun supportsLegacyPreferences(classLoader: ClassLoader): Boolean = runCatching {
        classLoader.loadClass(LEGACY_PREFERENCES_CLASS)
            .getDeclaredMethod(LEGACY_SETTER, java.lang.Boolean.TYPE)
    }.isSuccess

    private const val LEGACY_PREFERENCES_CLASS = "com.apple.android.music.utils.AppSharedPreferences"
    private const val LEGACY_SETTER = "setLyricsTranslationSelected"
}
