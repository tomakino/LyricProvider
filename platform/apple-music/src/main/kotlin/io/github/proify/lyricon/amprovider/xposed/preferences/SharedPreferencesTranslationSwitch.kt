/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.preferences

import android.content.Context
import android.content.SharedPreferences

/** 5.x / 6.x 的 SharedPreferences 存储。 */
internal class SharedPreferencesTranslationSwitch(
    context: Context,
    private val onChanged: (Boolean) -> Unit,
) : TranslationSwitch {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var installed = false

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY) onChanged(preferences.getBoolean(KEY, DEFAULT))
    }

    override val selected: Boolean
        get() = preferences.getBoolean(KEY, DEFAULT)

    override fun install() {
        if (installed) return
        installed = true
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    private companion object {
        const val PREFS_NAME = "preferences"
        const val KEY = "key_player_lyrics_translation_selected"
        const val DEFAULT = true
    }
}
