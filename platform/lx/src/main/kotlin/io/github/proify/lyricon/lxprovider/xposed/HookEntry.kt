/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lxprovider.xposed

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import io.github.proify.lyricon.lxprovider.xposed.variant.ikunshare.IKunMusic
import io.github.proify.lyricon.lxprovider.xposed.variant.lxnetease.LxNetease
import io.github.proify.lyricon.lxprovider.xposed.variant.main.LXMusic

@InjectYukiHookWithXposed(modulePackageName = Constants.PROVIDER_PACKAGE_NAME)
open class HookEntry : IYukiHookXposedInit {

    override fun onHook() = YukiHookAPI.encase {
        loadApp("cn.toside.music.mobile", LXMusic())
        loadApp("com.ikunshare.music.mobile", IKunMusic())
        loadApp("com.lxnetease.music.mobile", LxNetease())
    }

    override fun onInit() {
        YukiHookAPI.configs {
            debugLog {
                tag = "LXMusicProvider"
            }
        }
    }
}