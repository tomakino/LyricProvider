/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.localprovider.xposed

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

@InjectYukiHookWithXposed(modulePackageName = "io.github.proify.lyricon.localprovider")
open class HookEntry : IYukiHookXposedInit {

    override fun onHook() {
        YukiHookAPI.encase {
            // 原有 LocalProvider（支持 MediaSession 的通用播放器）
            loadApp(isExcludeSelf = true, LocalProvider)
            // 新增 PowerAmp 专用 Hooker（支持 PowerAmp 广播和内嵌歌词）
            loadApp("com.maxmpz.audioplayer", PowerAmp)
        }
    }

    override fun onInit() {
        YukiHookAPI.configs {
            debugLog {
                isEnable = true
                tag = "LocalProvider"
            }
        }
    }
}