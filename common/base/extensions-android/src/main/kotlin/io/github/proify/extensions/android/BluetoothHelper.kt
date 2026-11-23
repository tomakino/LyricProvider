/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.extensions.android

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.extension.kavaref.toClass
import com.highcapable.yukihookapi.hook.param.PackageParam

/**
 * @author Lin
 */
object BluetoothHelper {

    context(_: PackageParam)
    fun openBluetoothA2dpOn(classLoader: ClassLoader?) {
        if (classLoader == null) return

        "android.media.AudioManager".toClass(classLoader)
            .resolve()
            .firstMethod {
                name = "isBluetoothA2dpOn"
                emptyParameters()
            }.hook { intercept(true) }

        "android.bluetooth.BluetoothAdapter".toClass(classLoader)
            .resolve()
            .firstMethod {
                name = "isEnabled"
                emptyParameters()
            }.hook { intercept(true) }
    }

}