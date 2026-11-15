/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.qishuiprovider.xposed

import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule

@YukiHookLibXposedEntry(
    targetApiVersion = 101,
    scope = arrayOf("com.luna.music"),
    staticScope = true
)
open class HookEntry : YukiHookXposedModule {

    override fun onHook() =
        YukiHook.encase {
            loadApp("com.luna.music", QiShui)
        }

    override fun onInit() = YukiHook.configure {
        logging { tag = "QiShuiProvider" }
    }
}