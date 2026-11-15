/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.kgprovider.xposed

import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
import io.github.proify.lyricon.kgprovider.xposed.kugou.KuGou
import io.github.proify.lyricon.kgprovider.xposed.kugou.KuGouLite

@YukiHookLibXposedEntry(
    targetApiVersion = 101,
    scope = arrayOf("com.kugou.android", "com.kugou.android.lite"),
    staticScope = true
)
open class HookEntry : YukiHookXposedModule {

    override fun onHook() {
        YukiHook.encase {
            loadApp("com.kugou.android", KuGou())
            loadApp("com.kugou.android.lite", KuGouLite())
        }
    }

    override fun onInit() {
        super.onInit()
        YukiHook.configure {
            logging {
                tag = "KuGouMusicProvider"
            }
        }
    }
}