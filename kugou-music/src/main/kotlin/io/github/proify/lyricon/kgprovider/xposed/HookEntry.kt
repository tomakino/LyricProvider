/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.kgprovider.xposed

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import io.github.proify.lyricon.kgprovider.xposed.Constants.PROVIDER_PACKAGE_NAME
import io.github.proify.lyricon.kgprovider.xposed.kugou.KuGou
import io.github.proify.lyricon.kgprovider.xposed.kugou.KuGouLite

@InjectYukiHookWithXposed(modulePackageName = PROVIDER_PACKAGE_NAME)
open class HookEntry : IYukiHookXposedInit {

    override fun onHook() {
        YukiHookAPI.encase {
            loadApp("com.kugou.android", KuGou())
            loadApp("com.kugou.android.lite", KuGouLite())
        }
    }

    override fun onInit() {
        super.onInit()
        YukiHookAPI.configs {
            debugLog {
                tag = "KuGouMusicProvider"
            }
        }
    }
}