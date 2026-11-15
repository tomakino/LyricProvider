/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.proify.lyricon.retromusicprovider.xposed

import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule

@YukiHookLibXposedEntry(
    targetApiVersion = 101,
    scope = arrayOf("code.name.monkey.retromusic"),
    staticScope = true
)
class HookEntry : YukiHookXposedModule {
    override fun onHook() {
        YukiHook.encase {
            loadApp(Constants.MUSIC_PACKAGE_NAME, RetroMusic)
        }
    }

    override fun onInit() {
        super.onInit()
        YukiHook.configure {
            logging { tag = Constants.LOG_TAG }
        }
    }
}
