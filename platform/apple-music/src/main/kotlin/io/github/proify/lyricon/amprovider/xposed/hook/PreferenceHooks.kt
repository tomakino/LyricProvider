/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.amprovider.xposed.hook

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.hook.param.PackageParam
import io.github.proify.lyricon.amprovider.xposed.PreferencesMonitor
import io.github.proify.lyricon.amprovider.xposed.util.DebugLog

/** 翻译开关监听；全部候选失败时降级为"有翻译就展示"。 */
internal fun PackageParam.installPreferenceHooks(classLoader: ClassLoader) {
    for (target in PreferencesMonitor.resolveHookTargets(classLoader)) {
        if (tryHook(classLoader, target)) {
            PreferencesMonitor.markHookInstalled()
            break
        }
    }
    PreferencesMonitor.readInitialValue(classLoader)
}

private fun PackageParam.tryHook(
    classLoader: ClassLoader,
    target: PreferencesMonitor.HookTarget,
): Boolean = try {
    classLoader.loadClass(target.className)
        .resolve()
        .firstMethod {
            name = target.setterName
            parameters(Boolean::class.java)
        }
        .hook {
            after {
                (args.getOrNull(0) as? Boolean)?.let(PreferencesMonitor::dispatch)
            }
        }
    true
} catch (e: Throwable) {
    DebugLog.log("Apple", "偏好 hook 失败 ${target.className}: ${e.message}")
    false
}
