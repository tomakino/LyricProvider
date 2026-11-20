/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.preferences

/** 宿主歌词翻译开关。 */
internal interface TranslationSwitch {

    /** 当前开关状态。 */
    val selected: Boolean

    /** 开始监听变化；重复调用保持幂等。 */
    fun install()
}
