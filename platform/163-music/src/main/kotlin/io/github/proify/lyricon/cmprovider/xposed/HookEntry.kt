/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.cmprovider.xposed

import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
import io.github.proify.lyricon.cmprovider.xposed.Constants.HONOR_MUSIC_PACKAGE_NAME
import io.github.proify.lyricon.cmprovider.xposed.Constants.NETEASE_MUSIC_PACKAGE_NAME

@YukiHookLibXposedEntry(
    targetApiVersion = 101,
    scope = [
        NETEASE_MUSIC_PACKAGE_NAME,
        HONOR_MUSIC_PACKAGE_NAME
    ],
    staticScope = true,
)
class HookEntry : YukiHookXposedModule {

    override fun onHook() {
        YukiHook.encase {
            loadApp(NETEASE_MUSIC_PACKAGE_NAME, CloudMusic())
            loadApp(HONOR_MUSIC_PACKAGE_NAME, CloudMusic())
        }
    }

    override fun onInit() {
        YukiHook.configure {
            logging {
                tag = "CloudMusicProvider"
            }
        }
    }
}
