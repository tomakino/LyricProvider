/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

/** 宿主歌词偏好取值：关闭 / 翻译 / 音译。 */
internal object TranslationMode {
    const val DISABLED = -1
    const val TRANSLATION = 0
    const val ROMANIZATION = 1
}
