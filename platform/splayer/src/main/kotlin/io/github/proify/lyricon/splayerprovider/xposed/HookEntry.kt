/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.splayerprovider.xposed

import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule

@YukiHookLibXposedEntry(
    targetApiVersion = 101,
    scope = [
        "top.imsyy.splayer.android",
    ],
    staticScope = true
)
open class HookEntry : YukiHookXposedModule {

    override fun onHook() {
        YukiHook.encase {
            loadApp("top.imsyy.splayer.android", object : SPlayerLyricProvider() {
                override fun getPlaybackManager(): String = "$packageName.playback.PlaybackManager"
            })
        }
    }

    override fun onInit() {
        YukiHook.configure {
            logging {
                enabled = true
                tag = "SPlayerProvider"
            }
        }
    }
}
