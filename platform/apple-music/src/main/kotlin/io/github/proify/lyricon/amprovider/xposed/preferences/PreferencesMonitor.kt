/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.preferences

import android.content.Context

/** 歌词翻译开关：读取宿主持久化状态并监听变化。 */
internal class PreferencesMonitor(
    context: Context,
    classLoader: ClassLoader,
    onChanged: (Boolean) -> Unit,
) {

    private val switch = TranslationSwitchFactory.create(context, classLoader, onChanged)

    /** 当前开关状态。 */
    val displayTranslation: Boolean
        get() = switch.selected

    fun start() = switch.install()
}
