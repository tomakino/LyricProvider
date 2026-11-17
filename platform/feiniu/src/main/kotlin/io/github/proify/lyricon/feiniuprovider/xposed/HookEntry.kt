/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.feiniuprovider.xposed

import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
import io.github.proify.lyricon.feiniuprovider.xposed.Constants.MUSIC_PACKAGE_NAME

@YukiHookLibXposedEntry(
    targetApiVersion = 101,
    scope = [MUSIC_PACKAGE_NAME],
    staticScope = true
)
open class HookEntry : YukiHookXposedModule {

    override fun onHook() {
        YukiHook.encase {
            loadApp(MUSIC_PACKAGE_NAME, FeiniuMusic())
        }
    }

    override fun onInit() {
        super.onInit()
        YukiHook.configure {
            logging {
                tag = "FeiniuMusicProvider"
            }
        }
    }
}
