/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.paprovider.bridge

import com.highcapable.yukihookapi.hook.xposed.prefs.data.PrefsData

/**
 * 全局配置常量定义
 */
object Configs {
    // 使用 YukiHookAPI 的 PrefsData 包装 Key 和默认值
    val ENABLE_TRANSLATION = PrefsData("enable_translation", false)
    val ENABLE_NET_SEARCH = PrefsData("enable_net_search", false)
    //val ENABLE_AUTO_SAVE = PrefsData("enable_auto_save", false)
}