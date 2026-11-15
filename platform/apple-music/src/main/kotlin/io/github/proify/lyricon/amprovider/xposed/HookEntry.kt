/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

@InjectYukiHookWithXposed(modulePackageName = Constants.PROVIDER_PACKAGE_NAME)
open class HookEntry : IYukiHookXposedInit {

    override fun onHook() {
        YukiHookAPI.encase {
            loadApp(Constants.APPLE_MUSIC_PACKAGE_NAME, Apple)
        }
    }

    override fun onInit() {
        super.onInit()
        YukiHookAPI.configs {
            debugLog {
                tag = "AMProvider"
                isEnable = true
                elements(TAG, PRIORITY, PACKAGE_NAME, USER_ID)
            }
        }
    }
}