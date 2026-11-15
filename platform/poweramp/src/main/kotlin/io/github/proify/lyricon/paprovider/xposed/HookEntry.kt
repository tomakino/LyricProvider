/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.paprovider.xposed

import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
import com.highcapable.yukihookapi.hook.factory.configure
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule

@YukiHookLibXposedEntry(
    targetApiVersion = 101,
    scope = arrayOf("com.maxmpz.audioplayer", "com.maxmpz.audioplayer.unlock"),
    staticScope = true
)
class HookEntry : YukiHookXposedModule {

    override fun onHook() = encase {
        loadApp(name = "com.maxmpz.audioplayer", PowerAmp)
    }

    override fun onInit() = configure {
        debug = true
        logging {
            tag = Constants.LOG_TAG
        }
    }
}
