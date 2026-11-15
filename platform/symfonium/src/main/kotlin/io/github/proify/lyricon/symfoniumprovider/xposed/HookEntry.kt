/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.symfoniumprovider.xposed

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import io.github.proify.lyricon.symfoniumprovider.xposed.Constants.PROVIDER_PACKAGE_NAME

@InjectYukiHookWithXposed(modulePackageName = PROVIDER_PACKAGE_NAME)
open class HookEntry : IYukiHookXposedInit {

    override fun onHook() {
        YukiHookAPI.encase {
            loadApp("app.symfonik.music.player", Symfonium())
        }
    }

    override fun onInit() {
        YukiHookAPI.configs {
            debugLog {
                tag = "SymfoniumProvider"
            }
        }
    }
}