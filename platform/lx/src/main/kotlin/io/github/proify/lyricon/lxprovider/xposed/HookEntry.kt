/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lxprovider.xposed

import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
import io.github.proify.lyricon.lxprovider.xposed.variant.ikunshare.IKunMusic
import io.github.proify.lyricon.lxprovider.xposed.variant.lxnetease.LxNetease
import io.github.proify.lyricon.lxprovider.xposed.variant.main.LXMusic

@YukiHookLibXposedEntry(
    targetApiVersion = 101,
    scope = arrayOf(
        "cn.toside.music.mobile",
        "com.ikunshare.music.mobile",
        "com.lxnetease.music.mobile"
    ),
    staticScope = true
)
open class HookEntry : YukiHookXposedModule {

    override fun onHook() = YukiHook.encase {
        loadApp("cn.toside.music.mobile", LXMusic())
        loadApp("com.ikunshare.music.mobile", IKunMusic())
        loadApp("com.lxnetease.music.mobile", LxNetease())
    }

    override fun onInit() {
        YukiHook.configure {
            logging {
                tag = "LXMusicProvider"
            }
        }
    }
}