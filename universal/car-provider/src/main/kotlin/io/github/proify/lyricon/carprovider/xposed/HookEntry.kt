/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.carprovider.xposed

import com.highcapable.yukihookapi.YukiHook
import com.highcapable.yukihookapi.annotation.xposed.YukiHookLibXposedEntry
import com.highcapable.yukihookapi.hook.xposed.YukiHookXposedModule
import io.github.proify.lyricon.library.carprovider.CarProvider
import io.github.proify.lyricon.provider.ProviderLogo

@YukiHookLibXposedEntry(targetApiVersion = 101)
open class HookEntry : YukiHookXposedModule {

    override fun onHook() =
        YukiHook.encase {
            loadApp(
                excludeSelf = true, CarProvider(
                    providerPackageName = Constants.PROVIDER_PACKAGE_NAME,
                    logo = ProviderLogo.fromBase64(Constants.ICON)
                )
            )
        }

    override fun onInit() = YukiHook.configure {
        logging { tag = "CarProvider" }
    }
}